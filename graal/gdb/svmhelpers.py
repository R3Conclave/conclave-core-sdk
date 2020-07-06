#
# Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# pylint: skip-file

import sys
import os.path
if sys.version_info[0] != 3:
    pyversion = '.'.join(str(v) for v in sys.version_info[:3])
    message = (
        'Cannot load SubstrateVM debugging assistance for GDB from ' + os.path.basename(__file__)
        + ': it requires at least Python 3.x. You are running GDB with Python ' + pyversion
        + ' from ' + sys.executable + '. Read DEBUGGING.md for detailed instructions.'
    )
    raise AssertionError(message)

from contextlib import contextmanager
import gdb
import gdb.types
import gdb.printing
from gdb.FrameDecorator import FrameDecorator
from gdb.unwinder import Unwinder

import os
import re

_tracefile = None

def trace(msg):
    if _tracefile:
        _tracefile.write(('trace: %s\n' % msg).encode(encoding='utf-8', errors='strict'))
        _tracefile.flush()

class SVMUtil:
    use_pp = True
    use_hlrep = False
    with_addr = False
    absolute_addr = False

    hub_fieldname = '__hub__'
    compressed_ref_prefix = '_z_.'
    compressed_ref_re = re.compile(re.escape(compressed_ref_prefix) + r'(\w+\.)')

    selfref_parents = dict()
    selfref_cycles = set()
    selfref_check = True

    print_cstr_limit = 40
    print_array_limit = 10
    print_depth_limit = 1
    print_static_fields = False
    complete_svar = False
    hlreps = dict()
    deopt_stub_addr = 0
    ptr_uncompress_type = None

    @classmethod
    def strip_compression(cls, str_val):
        return cls.compressed_ref_re.sub(r'\1', str_val)

    @classmethod
    def is_compressed(cls, obj):
        return str(obj.type).startswith(cls.compressed_ref_prefix)

    @classmethod
    def addr_str(cls, obj):
        if not cls.absolute_addr and cls.is_compressed(obj):
            return ' @z(' + str(int(obj)) + ')'
        else:
            return ' @(' + hex(cls.addr_repr(obj)) + ')'

    @classmethod
    def selfref_reset(cls, current_prompt=None):
        trace('selfref_reset')
        cls.selfref_parents.clear()
        cls.selfref_cycles.clear()
        return None

    @classmethod
    def addr_repr(cls, obj):
        if not cls.is_compressed(obj):
            return int(obj)
        if not cls.ptr_uncompress_type:
            uncompress_type = gdb.lookup_type(cls.compressed_ref_prefix + 'class')
            cls.ptr_uncompress_type = uncompress_type.pointer()
        obj = gdb.Value(int(obj))
        obj = obj.cast(cls.ptr_uncompress_type)
        res = int(obj.dereference().address)
        return res

    @classmethod
    def is_selfref(cls, value):
        if not cls.selfref_check:
            return False
        try:
            # Filter out primitives (by trying to access the hub)
            value.type[SVMUtil.hub_fieldname]
        except:
            return False

        return cls.addr_repr(value) in cls.selfref_cycles

    @classmethod
    def add_selfref(cls, parent, child):
        if not cls.selfref_check:
            return child
        try:
            # Filter out primitives (by trying to access the hub)
            child.type[SVMUtil.hub_fieldname]

            (addr_child, addr_parent) = (cls.addr_repr(child), cls.addr_repr(parent))
            if cls.selfref_reachable(child, parent):
                # trace(' <add selfref %x>' % addr_child)
                cls.selfref_cycles.add(addr_child)
            else:
                # trace(' <add %x --> %x>' % (addr_child, addr_parent))
                cls.selfref_parents[addr_child] = addr_parent
        finally:
            return child

    @classmethod
    def selfref_reachable(cls, value, startpos):
        try:
            orig_node = cls.addr_repr(value)
            child = cls.addr_repr(startpos)
            if orig_node == child:
                return True

            depth = 0
            while True:
                if depth >= cls.print_depth_limit:
                    return True
                parent = cls.selfref_parents[child]
                # trace(' <orig_node %x: %x>' % (orig_node, parent))
                if parent == orig_node:
                    # trace(' <%x is reachable from %x>' % (orig_node, int(startpos)))
                    return True
                child = parent
                depth += 1
        except:
            return False

    @classmethod
    def get_javastr(cls, ptr_to_javastr, error_result='<Invalid String>'):
        try:
            trace('get_javastr for {}'.format(hex(int(ptr_to_javastr))))
            value = ptr_to_javastr['value']
            trace('get_javastr value {}'.format(hex(int(value))))
            if int(value) == 0:
                return None

            try:
                coder = int(ptr_to_javastr['coder'])
                trace('get_javastr coder ' + str(coder))
                # From Java 9 on, value is byte[] with latin_1 or utf-16_le
                codec = {
                    0: 'latin_1',
                    1: 'utf-16_le',
                }.get(coder)
                bytes_per_char = 1
            except:
                codec = 'utf-16' # Java 8 has a char[] with utf-16
                bytes_per_char = 2

            value_content = value['__array__']
            value_length = value['__length__']
            string_data = bytearray()
            for index in range(int(value_length)):
                mask = (1 << 8 * bytes_per_char) - 1
                code_unit = int(value_content[index] & mask)
                code_unit_as_bytes = code_unit.to_bytes(bytes_per_char, byteorder='little')
                string_data.extend(code_unit_as_bytes)
            result = string_data.decode(codec)
            trace('get_javastr returns {}'.format(result))
            return result
        except Exception as e:
            trace('<get_javastr exception: %s>' % e)
            return error_result

    @classmethod
    def get_hub(cls, obj):
        try:
            trace('get_hub for {}'.format(hex(int(obj))))
            hub = obj[cls.hub_fieldname]
            return hub
        except Exception as e:
            trace('<get_hub exception: %s>' % e)
            return None

    @classmethod
    def get_rtt_name(cls, obj):
        try:
            trace('get_rtt_name for {}'.format(hex(int(obj))))
            hub = cls.get_hub(obj)
            hub_name = hub['name']
            trace('get_rtt_name hub_name {}'.format(hex(int(hub_name))))
            rtt_name = cls.get_javastr(hub_name, None)
            return rtt_name
        except Exception as e:
            trace('<get_rtt_name exception: %s>' % e)
            return None

    @classmethod
    def cast_to_rtt(cls, obj):
        try:
            rttname = cls.get_rtt_name(obj)
            if not rttname:
                trace('<cast_to_rtt: invalid rttname')
                return obj

            array_dimension = rttname.count('[')
            if array_dimension > 0:
                rttname = rttname[array_dimension:]
            if rttname[0] == 'L':
                classname_end = rttname.find(';')
                rttname = rttname[1:classname_end]
            else:
                rttname = {
                    'Z': 'boolean',
                    'B': 'byte',
                    'C': 'char',
                    'D': 'double',
                    'F': 'float',
                    'I': 'int',
                    'J': 'long',
                    'S': 'short',
                }.get(rttname, rttname)
            for _ in range(array_dimension):
                rttname += '[]'

            if SVMUtil.is_compressed(obj):
                rttname = cls.compressed_ref_prefix + rttname

            if str(rttname) == str(obj.type):
                return obj

            dyntype = gdb.lookup_type(rttname)
            ptr_dyntype = dyntype.pointer()
            return obj.cast(ptr_dyntype)
        except Exception as e:
            trace('<cast_to_rtt exception: %s>' % e)
            return obj

    @classmethod
    def get_symbol_address(cls, symbol):
        try:
            output = SVMUtil.execout('info address ' + symbol)
            try:
                return int(output.split(' at address ')[1].split('.')[0], 16)
            except:
                return int(output.split('is at')[1].strip().split(' ')[0], 16)
        except:
            return None

    @classmethod
    def get_address_symbol(cls, address):
        try:
            output = SVMUtil.execout('info symbol ' + hex(address))
            symbol = str(output.split('(')[0])
            return symbol
        except:
            return None

    @classmethod
    def execout(cls, cmd):
        return gdb.execute(cmd, False, True)


class SVMPPString:
    def __init__(self, obj):
        # trace(' <SVMPPString>')
        self.obj = obj

    def to_string(self):
        value = SVMUtil.get_javastr(self.obj)
        if value is None:
            return 'null'
        value = repr(value)[1:-1]
        value = '"' + value + '"'
        if SVMUtil.with_addr:
            value += SVMUtil.addr_str(self.obj)
        return value


class SVMPPCString:
    def __init__(self, obj):
        # trace(' <SVMPPCString>')
        self.obj = obj

    def to_string(self):
        cstr = repr(self.get_cstr(self.obj, None))[1:-1]
        if not cstr:
            return 'Invalid CString' + SVMUtil.addr_str(self.obj)
        cstr = '"' + cstr + '"'
        if SVMUtil.with_addr:
            cstr += SVMUtil.addr_str(self.obj)
        return cstr

    @staticmethod
    def get_cstr(ptr_to_cstr, error_result='<Invalid String>'):
        try:
            outstr = u''
            for i in range(SVMUtil.print_cstr_limit):
                current_char = int(ptr_to_cstr.dereference()) & 0xff
                if current_char == 0:
                    break
                outstr += chr(current_char).encode('ascii', 'replace').decode('utf-8')
                ptr_to_cstr += 1
                if i + 1 == SVMUtil.print_cstr_limit:
                    outstr += u'...'
            return outstr
        except Exception as e:
            trace('<get_cstr exception: %s>' % e)
            return error_result


class SVMPPArray:
    def __init__(self, obj, length, array=None):
        # trace(' <SVMPPArray>')
        self.obj = obj
        self.selfref = SVMUtil.is_selfref(obj)
        self.length = length
        if not array:
            self.java = False
            self.array = obj
        else:
            self.java = True
            self.array = array

    def display_hint(self):
        return 'array'

    def rttname(self):
        return SVMUtil.strip_compression(str(self.array.type))

    def to_string(self):
        value = self.rttname()
        if self.java:
            value = value[:-3] + '[%d]' % self.length
        if self.selfref or SVMUtil.print_array_limit <= 0:
            value += ' = {...}'
        if SVMUtil.with_addr:
            value += SVMUtil.addr_str(self.obj)
        return value

    def __iter__(self):
        for i in range(int(self.length)):
            yield self.array[i]

    def children(self):
        if self.selfref or SVMUtil.print_array_limit <= 0:
            return
        for index, elem in enumerate(self):
            yield (str(index), SVMUtil.add_selfref(self.obj, elem))
            if index + 1 == SVMUtil.print_array_limit:
                yield (str(index+1), '...')
                break


class SVMPPClass:
    def __init__(self, obj, typename=None):
        # trace(' <SVMPPClass>')
        self.obj = obj
        self.typename = typename
        self.selfref = SVMUtil.is_selfref(obj)

    def rttname(self):
        return SVMUtil.strip_compression(SVMUtil.get_rtt_name(self.obj))

    def __getitem__(self, key):
        item = self.obj[key]
        ppitem = gdb.default_visualizer(item)
        return item if ppitem is None else ppitem

    def to_string(self):
        try:
            if not self.typename:
                self.typename = self.rttname()
            if self.selfref:
                self.typename += ' = {...}'
            if SVMUtil.with_addr:
                self.typename += SVMUtil.addr_str(self.obj)
            return self.typename
        except:
            return 'object'

    def children(self):
        if self.selfref:
            return
        for f in self.obj.type.target().fields():
            if not SVMUtil.print_static_fields:
                try:
                    f.bitpos  # bitpos attribute is not available for static fields
                except:  # use bitpos access exception to skip static fields
                    continue
            if str(f.name) == SVMUtil.hub_fieldname:
                continue
            yield (str(f.name), SVMUtil.add_selfref(self.obj, self.obj[str(f.name)]))


class SVMPPCombine:
    def __init__(self, *printers):
        self.printers = printers
        self._sep = '|'
        self._begin = ''
        self._end = ''

    def begin(self, s):
        self._begin = s
        return self

    def sep(self, s):
        self._sep = s
        return self

    def end(self, s):
        self._end = s
        return self

    def to_string(self):
        return self._begin + self._sep.join([printer.to_string() for printer in self.printers]) + self._end


class SVMPPConst:
    def __init__(self, val):
        self.val = val

    def to_string(self):
        return self.val


class SVMPrettyPrinter(gdb.printing.PrettyPrinter):
    def __init__(self):
        super().__init__('SubstrateVM')

    def __call__(self, val):
        trace('<lookup(type %s, type-code %s)>' % (val.type, val.type.code))
        if not SVMUtil.use_pp:
            return None

        try:
            # import pdb; pdb.set_trace()
            # Promote TYPEDEFs of runtime-compiled code to full types
            if val.type.code == gdb.TYPE_CODE_PTR:
                target_type = val.type.target()
                if target_type.code == gdb.TYPE_CODE_TYPEDEF:
                    full_type = gdb.lookup_type(str(target_type.name)).pointer()
                    val = val.cast(full_type)
                    trace('applied typedef to full_type conversion')

            trace('<lookup(type %s, type-code %s)> is primitive?' % (val.type, val.type.code))
            # Filter out primitives (by trying to access the hub)
            val.type[SVMUtil.hub_fieldname]

            # Filter out references to the null literal
            trace('<lookup(type %s, type-code %s)> is null?' % (val.type, val.type.code))
            if int(val) == 0:
                return SVMPPConst('null')

            # Convert object to its runtime type object
            trace('<lookup(type %s, type-code %s)> cast to rtt:' % (val.type, val.type.code))
            val = SVMUtil.cast_to_rtt(val)

            def strip_prefix(typestr):
                return typestr.split(SVMUtil.compressed_ref_prefix, 1)[-1]

            rttname = strip_prefix(str(val.type))
            if rttname == 'java.lang.String':
                return SVMPPString(val)

            try:
                # Array ?
                length = val['__length__']
                array = val['__array__']
                return SVMPPArray(val, length, array)
            except:
                pass

            try:
                # Enum ?
                name = val['name']
                ordinal = val['ordinal']
                if strip_prefix(str(name.type)) == 'java.lang.String':
                    enum_name = SVMUtil.get_javastr(name)
                    enum_pp = SVMPPCombine(SVMPPConst(enum_name), SVMPPConst(str(ordinal))).sep('(').end(')')
                    if SVMUtil.with_addr:
                        enum_pp = SVMPPCombine(enum_pp, SVMPPConst(SVMUtil.addr_str(val))).sep('')
                    return enum_pp
            except:
                pass

            # Any other Class ...
            pp = SVMPPClass(val)
            if SVMUtil.use_hlrep:
                pp = makeHighLevelObject(pp)
            return pp

        except:
            pass

        try:
            if val.type.code == gdb.TYPE_CODE_ARRAY:
                (_, high) = val.type.range()
                return SVMPPArray(val, high + 1)

            typename = str(val.type)
            if typename == 'char':
                charstr = repr(int(val).to_bytes(2, byteorder='little').decode('utf-16'))
                return SVMPPConst(charstr)
            if typename == 'byte':
                return SVMPPConst('%d' % val)
            if int(val) != 0:
                if typename.startswith('CStruct ') or typename.startswith('RawStructure '):
                    return SVMPPClass(val, typename)
                if typename.startswith('CPointer(char) '):
                    if SVMUtil.print_cstr_limit > 0:
                        return SVMPPCString(val)

        except:
            pass

        return None


def HLRep(original_class):
    try:
        SVMUtil.hlreps[original_class.target_type] = original_class
    except Exception as e:
        trace('<@HLRep registration exception: %s>' % e)
    return original_class


@HLRep
class ArrayList:
    target_type = 'java.util.ArrayList'
    def __init__(self, pp):
        self.size = int(pp['size'])
        self.elementData = pp['elementData']
        self.obj = pp.obj
        self.selfref = SVMUtil.is_selfref(self.obj)
    def to_string(self):
        res = 'java.util.ArrayList(' + str(self.size) + ')'
        if self.selfref:
            res += ' = {...}'
        if SVMUtil.with_addr:
            self.typename += SVMUtil.addr_str(self.obj)
        return res
    def display_hint(self):
        return 'array'
    def __iter__(self):
        for index, elem in enumerate(self.elementData):
            if index >= self.size:
                break
            yield elem
    def children(self):
        if SVMUtil.print_array_limit <= 0:
            return
        for index, elem in enumerate(self):
            yield (str(index), SVMUtil.add_selfref(self.obj, elem))
            if index + 1 == SVMUtil.print_array_limit:
                yield (str(index + 1), '...')
                break


def makeHighLevelObject(pp):
    try:
        trace('try makeHighLevelObject for ' + pp.rttname())
        hlrepclass = SVMUtil.hlreps[pp.rttname()]
        return hlrepclass(pp)
    except Exception as e:
        trace('<makeHighLevelObject exception: %s>' % e)
    return pp


class SVMCommandPrint(gdb.Command):
    '''Use this command to enable/disable SVM pretty printing'''
    def __init__(self):
        super().__init__('svm-print', gdb.COMMAND_USER)

    def complete(self, text, word):
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-print is %s' % {True : 'enabled', False : 'disabled'}.get(SVMUtil.use_pp))
        elif arg == 'off' or arg == 'disable':
            SVMUtil.use_pp = False
        else:
            SVMUtil.use_pp = True
SVMCommandPrint()


class SVMCommandUseHighLevel(gdb.Command):
    '''Use this command to enable/disable SVM high level representations'''
    def __init__(self):
        super().__init__('svm-use-hlrep', gdb.COMMAND_USER)

    def complete(self, text, word):
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-use-hlrep is %s' % {True : 'enabled', False : 'disabled'}.get(SVMUtil.use_hlrep))
        elif arg == 'off' or arg == 'disable':
            SVMUtil.use_hlrep = False
        else:
            SVMUtil.use_hlrep = True
SVMCommandUseHighLevel()


class SVMCommandPrintAddresses(gdb.Command):
    '''Use this command to enable/disable additionally printing the addresses'''
    def __init__(self):
        super().__init__('svm-print-address', gdb.COMMAND_USER)

    def complete(self, text, word):
        return [x for x in ['enable', 'absolute', 'disable'] if x.startswith(text)]

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-print-address is %s' % {True : 'enabled', False : 'disabled'}.get(SVMUtil.with_addr))
            print('with address mode %s' % {True : 'all absolute', False : 'normal'}.get(SVMUtil.absolute_addr))
        elif arg == 'off' or arg == 'disable':
            SVMUtil.with_addr = False
        elif arg == 'absolute':
            SVMUtil.absolute_addr = True
            SVMUtil.with_addr = True
        else:
            SVMUtil.absolute_addr = False
            SVMUtil.with_addr = True
SVMCommandPrintAddresses()


class SVMCommandSelfref(gdb.Command):
    '''Use this command to enable/disable cycle detection for pretty printing'''
    def __init__(self):
        super().__init__('svm-selfref-check', gdb.COMMAND_USER)

    def complete(self, text, word):
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-selfref-check is %s' % {True : 'enabled', False : 'disabled'}.get(SVMUtil.selfref_check))
        elif arg == 'off' or arg == 'disable':
            SVMUtil.selfref_check = False
        else:
            SVMUtil.selfref_check = True
            SVMUtil.selfref_reset()
SVMCommandSelfref()


class SVMCommandPrintCStringLimit(gdb.Command):
    '''Use this command to limit the number of characters shown during pretty printing of C strings (setting to 0 disables C string pretty printing).'''
    def __init__(self):
        super().__init__('svm-print-cstr-limit', gdb.COMMAND_USER)

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-print-cstr-limit current value %d' % SVMUtil.print_cstr_limit)
        else:
            SVMUtil.print_cstr_limit = int(arg)
SVMCommandPrintCStringLimit()


class SVMCommandPrintArrayLimit(gdb.Command):
    '''Use this command to limit the number of array elements shown during pretty printing (setting to 0 disables pretty printing of arrays).'''
    def __init__(self):
        super().__init__('svm-print-array-limit', gdb.COMMAND_USER)

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-print-array-limit current value %d' % SVMUtil.print_array_limit)
        else:
            SVMUtil.print_array_limit = int(arg)
SVMCommandPrintArrayLimit()


class SVMCommandPrintDepthLimit(gdb.Command):
    '''Use this command to limit the depth of recursive pretty printing.'''
    def __init__(self):
        super().__init__('svm-print-depth-limit', gdb.COMMAND_USER)

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-print-depth-limit current value %d' % SVMUtil.print_depth_limit)
        else:
            SVMUtil.print_depth_limit = int(arg)
SVMCommandPrintDepthLimit()


class SVMCommandPrintStaticFields(gdb.Command):
    '''Use this command to enable/disable printing of static field members'''
    def __init__(self):
        super().__init__('svm-print-static-fields', gdb.COMMAND_USER)

    def complete(self, text, word):
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-print-static-fields is %s' % {True : 'enabled', False : 'disabled'}.get(SVMUtil.print_static_fields))
        elif arg == 'on' or arg == 'enable':
            SVMUtil.print_static_fields = True
        else:
            SVMUtil.print_static_fields = False
SVMCommandPrintStaticFields()


class SVMCommandCompleteStaticVariables(gdb.Command):
    '''Use this command to enable/disable completion of static variables'''
    def __init__(self):
        super().__init__('svm-complete-static-variables', gdb.COMMAND_USER)

    def complete(self, text, word):
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg, from_tty):
        if arg == '':
            print('svm-complete-static-variables is %s' % {True : 'enabled', False : 'disabled'}.get(SVMUtil.complete_svar))
        elif arg == 'on' or arg == 'enable':
            SVMUtil.complete_svar = True
        else:
            SVMUtil.complete_svar = False
SVMCommandCompleteStaticVariables()


class SVMCommandCompleteDebugTrace(gdb.Command):
    '''Use this command to enable/disable debug tracing for svmhelpers.py'''
    def __init__(self):
        super().__init__('svm-debug-tracing', gdb.COMMAND_USER)

    def complete(self, text, word):
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg, from_tty):
        global _tracefile
        if arg == '':
            print('svm-debug-tracing is %s' % {True : 'enabled', False : 'disabled'}.get(bool(_tracefile)))
        elif arg == 'on' or arg == 'enable':
            if not _tracefile:
                _tracefile = open('svmhelpers.trace.out', 'ab', 0)
        else:
            if _tracefile:
                _tracefile.close()
                _tracefile = None
SVMCommandCompleteDebugTrace()


class SVMCommandPrettyPrint(gdb.Command):
    '''Use this command for SVM pretty printing'''
    def __init__(self):
        super().__init__('pp', gdb.COMMAND_DATA)
        self.last = None
        self.svar_cache = None

    @staticmethod
    def fetchfields(value):
        trace('fetchfields)')
        if value == None:
            return []
        ppobj = gdb.default_visualizer(value)
        if ppobj == None:
            return []
        # For arrays we want to prevent this (the list could get huge)
        if ppobj.__class__.__name__ == 'SVMPPArray':
            return []
        trace('fetchfields for %s returned children' % ppobj.to_string())
        return ppobj.children()

    @staticmethod
    def deref(lhs, rhs):
        for (fieldname, fieldvalue) in SVMCommandPrettyPrint.fetchfields(lhs):
            if fieldname == rhs:
                return fieldvalue
        return None

    @staticmethod
    def splitindex(identifier):
        indices = []
        if '[' in identifier:
            (identifier, sep, after) = identifier.partition('[')
            parts = (sep + after).split('][')
            try:
                for part in parts:
                    indices.append(int(part.strip('[]')))
            except:
                indices = []
        trace('splitindex result (%s, %s)' % (identifier, indices))
        return identifier, indices

    @staticmethod
    def getelem(value, indices):
        if len(indices) == 0:
            return value
        try:
            for index in indices:
                if value == None:
                    return value
                valuepp = gdb.default_visualizer(value)
                value = None
                if valuepp == None:
                    return None
                trace('<getelem for index %d fetch children of: %s>' % (index, valuepp.__class__.__name__))
                children = valuepp.children()
                for (elemname, elemvalue) in children:
                    trace('<getelem at child: %s>' % elemname)
                    if index == 0:
                        value = elemvalue
                        break
                    index -= 1
        except Exception as e:
            trace('<getelem exception: %s>' % e)
            value = None
        return value

    def resolve_primary(self, field_access_str):
        parts = field_access_str.split('.')
        primary = None
        for index, part in enumerate(parts):
            (part, sep, after) = part.partition('[')
            if index == 0 and part == '$last':
                if self.last:
                    return self.last, parts
                else:
                    break
            if not primary:
                primary = part
            else:
                primary += '.' + part
            try:
                trace('<resolve_primary gdb.parse_and_eval: %s>' % primary)
                if primary.startswith('@'):
                    if primary.startswith('@z'):
                        skip = 2
                        prefix = SVMUtil.compressed_ref_prefix
                    else:
                        skip = 1
                        prefix = ''
                    temp_gdb_varname = '$obj_as_addr'
                    SVMUtil.execout('set {} = ({}java.lang.Object) {}'.format(temp_gdb_varname, prefix, primary[skip:]))
                    primary = temp_gdb_varname
                value = gdb.parse_and_eval("'" + primary + "'" if '.' in primary else primary)
                if value != None:
                    resolved_parts = parts[index:]
                    resolved_parts[0] = primary + sep + after
                    return value, resolved_parts
            except Exception as e:
                trace('<resolve_primary exception: %s>' % e)
        return None, parts

    def resolve(self, field_access_str):
        trace('Resolving <%s>' % field_access_str)
        (primary, parts) = self.resolve_primary(field_access_str)
        if primary == None:
            return None
        trace('<resolve_primary success: %s>' % parts)
        (identifier, index) = SVMCommandPrettyPrint.splitindex(parts[0])
        current = SVMCommandPrettyPrint.getelem(primary, index)
        for identifier in parts[1:]:
            (identifier, index) = SVMCommandPrettyPrint.splitindex(identifier)
            current = SVMCommandPrettyPrint.deref(current, identifier)
            current = SVMCommandPrettyPrint.getelem(current, index)
            if current == None:
                break
        return current

    @staticmethod
    @contextmanager
    def lookup_scope():
        trace('lookupScope {')
        print_array_limit_bak = SVMUtil.print_array_limit
        print_depth_limit_bak = SVMUtil.print_depth_limit
        selfref_check_bak = SVMUtil.selfref_check
        SVMUtil.print_array_limit = 2**31
        SVMUtil.print_depth_limit = 2**31
        SVMUtil.selfref_check = False
        SVMUtil.selfref_reset()
        yield
        SVMUtil.selfref_check = selfref_check_bak
        SVMUtil.print_array_limit = print_array_limit_bak
        SVMUtil.print_depth_limit = print_depth_limit_bak
        trace('lookupScope }')

    def svar_complete(self, text, other_candidates):
        if not SVMUtil.complete_svar:
            return []

        trace("svar_complete for '%s'" % text)
        if not self.svar_cache:
            trace('building svar_cache')
            self.svar_cache = ('root', [])
            output = SVMUtil.execout('info variables')
            for line in output.split('\n'):
                if not line.startswith('static '):
                    continue
                startpos = line.rfind(' ')
                staticvarname = line[startpos + 1:-1]
                currentnode = self.svar_cache
                for part in staticvarname.split('.'):
                    found = None
                    for childnode in currentnode[1]:
                        if childnode[0] == part:
                            found = childnode
                            break
                    if found:
                        currentnode = found
                    else:
                        newnode = (part, [])
                        currentnode[1].append(newnode)
                        currentnode = newnode

        candidates = []
        try:
            currentnode = self.svar_cache
            lastfindings = []
            textparts = text.split('.')
            partsindex = 0
            partsused = 0
            appending = False
            while len(textparts) > partsindex:
                part = textparts[partsindex]
                islast = (part == textparts[-1])
                findings = []
                for childnode in currentnode[1]:
                    if islast or appending:
                        accept = childnode[0].startswith(part)
                    else:
                        accept = childnode[0] == part
                    if accept:
                        findings.append(childnode)

                exactmatch = None
                if len(findings) == 1 and len(other_candidates) == 0:
                    exactmatch = findings[0]

                if exactmatch:
                    currentnode = exactmatch
                    textparts[partsindex] = currentnode[0]
                    partsindex += 1
                    if partsindex >= len(textparts):
                        textparts.append('')
                        appending = True
                    else:
                        partsused += 1
                else:
                    lastfindings = [entry[0] for entry in findings]
                    break

            # trace('svar_complete lastfindings %s' % str(lastfindings))
            # trace('svar_complete textparts %s' % str(textparts))

            resultparts = textparts[partsused:partsindex]
            if not lastfindings:
                if resultparts:
                    candidates.append('.'.join(resultparts))
            else:
                for finding in lastfindings:
                    candidates.append('.'.join(resultparts + [finding]))

        except Exception as e:
            trace('<svar_complete exception: %s>' % e)

        trace('svar_complete candidates %s' % candidates)
        return candidates

    def complete(self, text, word):
        with SVMCommandPrettyPrint.lookup_scope():
            trace('text="%s"' % text)
            if text.rfind('[') > text.rfind('.'):
                try:
                    # trace('array index completion')
                    (field_access_str, _, after) = text.rpartition('[')
                    if ']' in after:
                        return []
                    value = self.resolve(field_access_str)
                    if value == None:
                        return []
                    ppobj = gdb.default_visualizer(value)
                    if ppobj.__class__.__name__ == 'SVMPPArray':
                        candidates = []
                        arrlen = ppobj.length
                        for arrindex in range(arrlen):
                            if arrindex > 2:
                                candidates.append('%d]' % (arrlen - 1))
                                break
                            candidates.append('%d]' % arrindex)
                        return [c for c in candidates if c.startswith(after)]
                except Exception as e:
                    trace('<arrayindex completion exception: %s>' % e)
                    return []

            if not '.' in text:
                gdb.flush()
                candidates = []
                use_pp_bak = SVMUtil.use_pp
                SVMUtil.use_pp = False
                output = SVMUtil.execout('info locals')
                output += SVMUtil.execout('info args')
                SVMUtil.use_pp = use_pp_bak
                output_skiplist = ['<optimized out>', 'No locals.', 'No arguments.']
                for line in output.split('\n'):
                    if any([blentry in line for blentry in output_skiplist]):
                        continue
                    words = line.split('=')
                    if len(words) > 0:
                        first = words[0]
                        first = first.strip()
                        if len(first) > 0:
                            candidates.append(first)
                candidates = [x for x in candidates if x.startswith(text)]
                candidates += self.svar_complete(text, candidates)
                return candidates

            if text.endswith('.'):
                field_access_str = text.rstrip('.')
                candidates = [fieldname for (fieldname, _) in SVMCommandPrettyPrint.fetchfields(self.resolve(field_access_str))]
                candidates += self.svar_complete(text, candidates)
                return candidates

            if '.' in text:
                (before, _, after) = text.rpartition('.')
                fields = SVMCommandPrettyPrint.fetchfields(self.resolve(before))
                candidates = [fieldname for (fieldname, _) in fields if fieldname.startswith(after)]
                candidates += self.svar_complete(text, candidates)
                return candidates

            return []

    def invoke(self, arg, from_tty):
        try:
            with SVMCommandPrettyPrint.lookup_scope():
                res = self.resolve(arg)
            self.svar_cache = None
            if res != None:
                self.last = res
                print(str(res))
            else:
                print('No Java debug-expression "%s" in current context.' % arg)
        except KeyboardInterrupt:
            pass
SVMCommandPrettyPrint()


class SVMCommandBreak(gdb.Command):
    '''Use this command for setting breakpoints'''
    def __init__(self):
        super().__init__('bb', gdb.COMMAND_BREAKPOINTS)
        # GDB seems to use POSIX basic regular expressions where () and {} have no
        # special meaning so we use our own replacement instead of re.escape(), and
        # we also treat '.' and '$' the same to simplify matching inner classes
        self.gdb_re_escape_map = {i: '\\' + chr(i) for i in b'[]?*+-|^\\&~# \t\n\r\v\f'}
        self.gdb_re_escape_map.update({c: r'[\.\$]' for c in b'.$'})

    def escape_and_normalize(self, pattern):
        return pattern.translate(self.gdb_re_escape_map)

    def findmatch(self, search_text):
        matches = []
        search_text = self.escape_and_normalize(search_text)
        if not '(' in search_text and not ')' in search_text:
            search_text += '.*('
        cs_verbose = SVMUtil.execout('show case-sensitive')
        restore_cs = 'on' if '"on"' in cs_verbose else ('auto' if '"auto' in cs_verbose else None)
        if restore_cs:
            SVMUtil.execout('set case-sensitive off')
        # No need to quote the regex for gdb, it takes everything after 'info functions '
        for line in SVMUtil.execout('info functions ' + search_text).split('\n'):
            if not line.startswith('static '):
                continue
            if line.startswith('static CPointer') or line.startswith('static CStruct') or line.startswith('static RawStructure'):
                beginpos = line.find(' ', 14) + 1 # find end of CPointer/CStruct/RawStructure part
            else:
                beginpos = 7 # startpos for whitespace search
            # strip C-style return value declaration and append
            bp_entry = line[line.find(' ', beginpos):].lstrip(' []*').rstrip(' ;')
            matches.append(bp_entry)
        if restore_cs:
            SVMUtil.execout('set case-sensitive ' + restore_cs)
        return matches

    @staticmethod
    @contextmanager
    def pagination_off():
        gdb.execute('set pagination off')
        yield
        gdb.execute('set pagination on')

    def invoke(self, arg, from_tty):
        try:
            gdb.execute('tui disable')
            while True:
                if arg and not arg.startswith(':'):
                    options = []
                    try:
                        for index, line in enumerate(self.findmatch(arg)):
                            print('{0:2}: {1}'.format(index,line))
                            options.append(line)
                    except KeyboardInterrupt:
                        pass
                else:
                    print(':h for help, :q (or CTRL-D) to quit')
                while True:
                    index_or_substr = input('bb> ')
                    if index_or_substr == ':h':
                        print('\nSpecify a new function search expression or select from search results.')
                        print('\nExamples:')
                        print('Return a list of all ArrayList constructors:')
                        print('  bb> .ArrayList.<init>')
                        print('Set breakpoint for the first search result:')
                        print('  bb> 0')
                        print('Set breakpoint for the last search result:')
                        print('  bb> -1')
                        print('Any python array indexing/slicing is allowed:')
                        print('  bb> : (make breakpoints for all search results)')
                        print('  bb> 3:5 (make breakpoints for search result 3 and 4)\n')
                        continue
                    break
                if index_or_substr == ':q':
                    return
                try:
                    breakpoints = eval('options[' + index_or_substr + ']')
                    if isinstance(breakpoints, str):
                        print('Setting breakpoint for {}'.format(breakpoints))
                        gdb.execute("break '{}'".format(breakpoints))
                    else:
                        with SVMCommandBreak.pagination_off():
                            for bp in breakpoints:
                                print('Setting breakpoint for {}'.format(bp))
                                gdb.execute("break '{}'".format(bp))
                    return
                except:
                    arg = index_or_substr
        except EOFError:
            self.breakpoints = None
            print()
SVMCommandBreak()


class ThreadStackPrinterPrintBacktraceBP(gdb.Breakpoint):
    BreakpointSpec = 'com.oracle.svm.core.stack.ThreadStackPrinter.printBacktrace'
    @staticmethod
    def installOnce():
        if not re.search(ThreadStackPrinterPrintBacktraceBP.BreakpointSpec, SVMUtil.execout('maint info breakpoints')):
            ThreadStackPrinterPrintBacktraceBP()
            return True
        return False

    def __init__(self):
        super().__init__(ThreadStackPrinterPrintBacktraceBP.BreakpointSpec, gdb.BP_BREAKPOINT, internal=True)

    def stop(self):
        print('== PrintExceptionStackTrace: Print StackTrace with GDB')
        with SVMCommandBreak.pagination_off():
            gdb.execute('backtrace')
            print()
            lineStart = 'File '
            lineStartLen = len(lineStart)
            for line in SVMUtil.execout('info functions').split('\n'):
                if line.startswith(lineStart) and 'at 0x' in line:
                    print('== InstalledCode: ' + line[lineStartLen:-1])
            print()
        return False


class SVMFrameUnwinder(Unwinder):

    class FrameId(object):
        def __init__(self, sp, pc):
            self.sp = sp
            self.pc = pc

    AMD64_RBP = 6
    AMD64_RSP = 7
    AMD64_RIP = 16

    def __init__(self):
        super().__init__('SubstrateVM FrameUnwinder')
        self.stack_type = gdb.lookup_type('long')
        self.deopt_frame_type = gdb.lookup_type('com.oracle.svm.core.deopt.DeoptimizedFrame')

    def __call__(self, pending_frame):
        try:
            rsp = pending_frame.read_register(self.AMD64_RSP)
            rip = pending_frame.read_register(self.AMD64_RIP)
            if int(rip) == SVMUtil.deopt_stub_addr:
                deopt_frame_stack_slot = rsp.cast(self.stack_type.pointer()).dereference()
                deopt_frame = deopt_frame_stack_slot.cast(self.deopt_frame_type.pointer())
                source_frame_size = deopt_frame['sourceTotalFrameSize']
                # Now find the register-values for the caller frame
                unwind_info = pending_frame.create_unwind_info(SVMFrameUnwinder.FrameId(rsp, rip))
                caller_rsp = rsp + int(source_frame_size)
                unwind_info.add_saved_register(self.AMD64_RSP, gdb.Value(caller_rsp))
                caller_rip = gdb.Value(caller_rsp - 8).cast(self.stack_type.pointer()).dereference()
                unwind_info.add_saved_register(self.AMD64_RIP, gdb.Value(caller_rip))
                return unwind_info
        except:
            pass # Fallback to default frame unwinding via debug_frame (dwarf)

        return None


class SVMFrameFilter():
    def __init__(self):
        self.name = "SubstrateVM FrameFilter"
        self.priority = 100
        self.enabled = True

    def filter(self, frame_iter):
        for frame in frame_iter:
            frame = frame.inferior_frame()
            if SVMUtil.deopt_stub_addr and frame.pc() == SVMUtil.deopt_stub_addr:
                yield SVMFrameDeopt(frame)
            else:
                yield SVMFrame(frame)

class SVMFrame(FrameDecorator):
    def function(self):
        frame = self.inferior_frame()
        if not frame.name():
            return 'Unknown Frame at ' + hex(int(frame.read_register('sp')))
        func_name = str(frame.name().split('(')[0])
        if frame.type() == gdb.INLINE_FRAME:
            func_name = '<-- ' + func_name

        filename = self.filename()
        if filename:
            line = self.line()
            if line is None:
                line = 0
            eclipse_filename = '(' + os.path.basename(filename) + ':' + str(line) + ')'
        else:
            eclipse_filename = ''

        return func_name + eclipse_filename


class SVMFrameDeopt(SVMFrame):
    def function(self):
        return '[DEOPT FRAMES ...]'

    def frame_args(self):
        return None

    def frame_locals(self):
        return None


class SVMBreakpoint(gdb.Breakpoint):
    def __init__(self, spec, predicate):
        super().__init__(spec, gdb.BP_BREAKPOINT)
        self.predicate = predicate

    def stop(self):
        return self.predicate()


class SVMBreakCommand(gdb.Command):
    def __init__(self):
        super().__init__('svm-break', gdb.COMMAND_BREAKPOINTS)

    def invoke(self, arg, from_tty):
        args = arg.split()
        if len(args) == 1:
            SVMBreakpoint(args[0], lambda: True)
        elif len(args) > 3 and args[1] == "if":
            expr = compile(' '.join(args[2:]), '<input>', 'eval')
            SVMBreakpoint(args[0], lambda: eval(expr))
        else:
            print ("Syntax: svm-break LOCATION [if PYTHON-CONDITION]")
SVMBreakCommand()


class SVMCatchCommand(gdb.Command):
    def __init__(self):
        super().__init__('svm-catch', gdb.COMMAND_BREAKPOINTS)

    def invoke(self, arg, from_tty):
        SVMBreakpoint('com.oracle.svm.core.snippets.SnippetRuntime.unwindException', lambda: arg == SVMUtil.get_rtt_name(gdb.parse_and_eval('exception')))
SVMCatchCommand()


try:
    svm_objfile = gdb.current_objfile()
    # Only if we have an objfile and an SVM specific symbol we consider this an SVM objfile
    if svm_objfile and SVMUtil.get_symbol_address('graal_create_isolate'):
        try:
            svminitfile = os.path.expandvars('${SVMGDBINITFILE}')
            exec(open(svminitfile).read())
            trace('successfully processed svminitfile: %s' % svminitfile)
        except Exception as e:
            trace('<exception in svminitfile execution: %s>' % e)

        gdb.printing.register_pretty_printer(svm_objfile, SVMPrettyPrinter())
        gdb.prompt_hook = SVMUtil.selfref_reset

        SVMUtil.deopt_stub_addr = SVMUtil.get_symbol_address('com.oracle.svm.core.deopt.Deoptimizer.deoptStub')

        if SVMUtil.deopt_stub_addr:
            SVMUtil.frame_unwinder = SVMFrameUnwinder()
            gdb.unwinder.register_unwinder(svm_objfile, SVMUtil.frame_unwinder)

        ThreadStackPrinterPrintBacktraceBP.installOnce()

        SVMUtil.frame_filter = SVMFrameFilter()
        if sys.platform.startswith('darwin'):
            gdb.frame_filters[SVMUtil.frame_filter.name] = SVMUtil.frame_filter
        else:
            svm_objfile.frame_filters[SVMUtil.frame_filter.name] = SVMUtil.frame_filter
    else:
        print('Warning: Load ' + os.path.basename(__file__) + ' only in the context of an SVM objfile')

    # Workaround to ensure native-images loaded as shared objects will have pretty printing support.
    # To fix this properly we need a later version of GDB (that provides Objfile.lookup_global_symbol).
    def register_new_objectfile(newObjFileEvent):
        svm_objfile = newObjFileEvent.new_objfile
        trace('Registering SVMPrettyPrinter for ' + svm_objfile.filename)
        gdb.printing.register_pretty_printer(svm_objfile, SVMPrettyPrinter())
    gdb.events.new_objfile.connect(register_new_objectfile)

except Exception as e:
    print('<exception in svmhelper initialization: %s>' % e)

# vim: tabstop=8 expandtab shiftwidth=4 softtabstop=4
