# Running JavaScript and Python code inside an enclave

When you use the `graalvm-native-image` runtime you have the option to enable JavaScript or Python language support.
When enabled you can create a context in which you can load and execute a JavaScript or Python source module.
This module can either run to completion and terminate, or it can define a set of functions and variables that
can subsequently be called and accessed from your Java enclave code.

## Modifying the sample enclave to use JavaScript/Python

The [sample "hello world" enclave](writing-hello-world.md) took us through a tutorial on how to write an
enclave that takes a string, reverses it and returns it via the host to the client. In this section we
will take the code from the sample and modify it to reverse the string using JavaScript and Python to replace the
existing Java `reverse()` function.

Make sure you've already run through the [tutorial](writing-hello-world.md) and have a working sample
application as a starting point.

### Enable JavaScript/Python in the conclave configuration

Enabling JavaScript or Python support in an enclave requires pulling in a number of dependencies that result in a
larger enclave size. Therefore, JavaScript and Python are not enabled by default. In order to enable support add the
following line to your enclave `build.gradle`.

=== "Javascript"
    ```groovy hl_lines="4"
    conclave {
        productID = 1
        revocationLevel = 0
        supportLanguages = "js"
    }
    ```
=== "Python"
    ```groovy hl_lines="4"
    conclave {
        productID = 1
        revocationLevel = 0
        supportLanguages = "python"
    }
    ```

See [here more details on enclave configuration](enclave-configuration.md).

### Import the GraalVM SDK classes

We need to use the GraalVM SDK to build a context in which to run the JavaScript or Python code. The dependency to 
the SDK is automatically added when you specify `supportLanguages = "js"` for Javascript or `supportLanguages = "python"` for python. 
So you just need to add the following import statements to `ReverseEnclave.java`:

```java hl_lines="3 4"
import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
```

### Parse the JavaScript/Python code and set up bindings to Java

The next step is to define and parse our JavaScript/Python code. Once we have done this we can set up the bindings
to Java which allow us to access the functions and variables that are defined within the JavaScript/Python
code. Add a constructor that prepares the JavaScript/Python context:

=== "Javascript"
    ```java hl_lines="4-15"
        // We store the previous result to showcase that the enclave internals can be examined in a mock test.
        byte[] previousResult;
    
        private final Value bindings;
        private static final String jsCode = "function reverse(input) {"
                + "  var split = input.split('');"
                + "  var reverse = split.reverse();"
                + "  return reverse.join('');"
                + "}";
    
        public ReverseEnclave() {
            Context context = Context.create("js");
            bindings = context.getBindings("js");
            context.eval("js", jsCode);
        }
    
        @Override
        protected byte[] receiveFromUntrustedHost(byte[] bytes) {
    ```
=== "Python"
    ```java hl_lines="4-12"
        // We store the previous result to showcase that the enclave internals can be examined in a mock test.
        byte[] previousResult;

        private final Context context;
        private final Value bindings;
        private static final String pythonCode = "def reverse(input):\n"
                + " return input[::-1]";
    
        public ReverseEnclave() {
            context = Context.create("python");
            bindings = context.getBindings("python");
            context.eval("python", pythonCode);
        }

        @Override
        protected byte[] receiveFromUntrustedHost(byte[] bytes) {
    ```

!!! Warning
    This warning only applies to Python code. The context that runs the Python code must be closed before the enclave is destroyed otherwise
    the application will hang once the method destroyEnclave is invoked. For now, the best place to close the context
    is at the bottom of the ```receiveMail``` method as shown below:
    ```java hl_lines="13-16"

        @Override
        protected void receiveMail(long id, EnclaveMail mail, String routingHint) {
            // This is used when the host delivers a message from the client.
            // First, decode mail body as a String.
            final String stringToReverse = new String(mail.getBodyAsBytes());
            // Reverse it and re-encode to UTF-8 to send back.
            final byte[] reversedEncodedString = reverse(stringToReverse).getBytes();
            // Get the post office object for responding back to this mail and use it to encrypt our response.
            final byte[] responseBytes = postOffice(mail).encryptMail(reversedEncodedString);
            postMail(responseBytes, routingHint);
        
            // Please ensure that close is called before or while the enclave is being destroyed. Failing to do so
            // will hang the application when the method destroyEnclave is called
            context.close();
        }
    ```

### Replace the Java function with a call to the JavaScript/Python function

Remove the Java code that reverses the string and replace it with the following function. Note
that `static` has been removed from the function declaration because it now accesses the `bindings`
member variable.

```java hl_lines="4-7"
        return result;
    }

    private String reverse(String input) {
        Value result = bindings.getMember("reverse").execute(input);
        return result.asString();
    }

    @Override
    protected void receiveMail(long id, EnclaveMail mail, String routingHint) {
```

Finally, run the sample code as described in the tutorial. The result should be the same: the string passed 
as an argument to the client is reversed and returned to the client.

!!! Warning
    Build times can be quite long when running in simulation mode. For productivity reasons,
    it is advisable to run the sample in mock mode. Before running the sample in mock mode, it is necessary
    to:

    1. Download [graalvm-ce-java11](https://github.com/graalvm/graalvm-ce-builds/releases).
    1. Set the environment variable ```JAVA_HOME``` to point to the GraalVM that was previously downloaded. For instance, 
    ```export JAVA_HOME=/usr/lib/jvm/graalvm-ce-java11-21.1.0)```.
    1. Update the environment variable PATH by running ```export PATH=JAVA_HOME/bin:$PATH```.
    1. For Python only - Install the Python component by running the command ```gu install python```.

## More information

Conclave support for JavaScript and Python is provided by the polyglot capabilities in GraalVM. Refer to
the [GraalVM documentation on embedding languages](https://www.graalvm.org/reference-manual/embed-languages/)
for detailed instructions on how to use this capability.

!!! note
    The functionality described on this page involves JIT compilation within the secure enclave. Due to the
    unavailability of the CPUID instruction in SGX enclaves, some optimisations which depend on the presence of
    certain instruction set extensions may not take place and performance may be degraded.

!!! warning
    The processor of the host system must support the SSE and SSE2 instruction set extensions. If these extensions
    are not present, the enclave may abort unexpectedly.

!!! warning
    Python support is limited and there are known vulnerabilities in the `pip` version used by our GraalVM version.
    At this point, there are no known versions of `pip` that have those vulnerabilities fixed, so it should be
    used at the user's risk.

    | Vulnerabilities |
    |-----------------|
    | [CVE-2018-20225](http://web.nvd.nist.gov/view/vuln/detail?vulnId=CVE-2018-20225)|
    | [CVE-2019-20907](http://web.nvd.nist.gov/view/vuln/detail?vulnId=CVE-2019-20907)|
    | [CVE-2020-26137](http://web.nvd.nist.gov/view/vuln/detail?vulnId=CVE-2020-26137)|
    | [CVE-2021-3572](http://web.nvd.nist.gov/view/vuln/detail?vulnId=CVE-2021-3572)  |