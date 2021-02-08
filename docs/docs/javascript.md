# Running JavaScript code inside an enclave

When you use the `graalvm-native-image` runtime you have the option to enable JavaScript language support.
When enabled you can create a context in which you can load and execute a JavaScript source module.
This module can either run to completion and terminate or it can define a set of functions and variables that
can subsequently be called and accessed from your Java enclave code.

## Modifying the sample enclave to use JavaScript

The [sample "hello world" enclave](writing-hello-world.md) took us through a tutorial on how to write an
enclave that takes a string, reverses it and returns it via the host to the client. In this section we
will take the code from the sample and modify it to reverse the string using JavaScript to replace the
existing Java `reverse()` function.

Make sure you've already run through the [tutorial](writing-hello-world.md) and have a working sample
application as a starting point.

### Enable JavaScript in the conclave configuration

Enabling JavaScript support in an enclave requires pulling in a number of dependencies that result in a
larger enclave size. Therefore, JavaScript is not enabled by default. In order to enable support add the
following line to your enclave `build.gradle`.

```groovy hl_lines="4"
conclave {
    productID = 1
    revocationLevel = 0
    supportLanguages = "js"
}
```

See [here more details on enclave configuration](enclave-configuration.md).

### Import the GraalVM SDK classes

We need to use the GraalVM SDK to build a context in which to run the JavaScript code. The dependency to 
the SDK is automatically added when you specify `supportLanguages = "js"` so you just need to add the 
following import statements to `ReverseEnclave.java`:

```java hl_lines="4 5"
import com.r3.conclave.enclave.Enclave;
import com.r3.conclave.mail.EnclaveMail;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
```

### Parse the JavaScript code and set up bindings to Java

The next step is to define and parse our JavaScript code. Once we have done this we can setup the bindings
to Java which allow us to access the functions and variables that are defined within the JavaScript
code. Add a constructor that prepares the JavaScript context:

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
    protected void receiveMail(long id, EnclaveMail mail, String routingHint) {
```

### Replace the Java function with a call to the JavaScript function

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

## More information

Conclave support for JavaScript is provided by the polyglot capabilities in GraalVM. Refer to
the [GraalVM documentation on embedding languages](https://www.graalvm.org/reference-manual/embed-languages/)
for detailed instructions on how to use this capability.

