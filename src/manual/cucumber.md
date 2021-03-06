## Cucumber BDD support

Behavior driven development (BDD) is becoming more and more popular these days. The idea of defining and describing the software behavior as basis for all tests in prior to translating those feature descriptions into executable tests is a very interesting approach because it includes the technical experts as well as the domain experts. With BDD the domain experts can easily read and verify the tests and the technical experts get a detailed description of what should happen in the test.

The test scenario descriptions follow the Gherkin syntax with a **"Given-When-Then"** structure most of the time. The Gherkin language is business readable and well known in BDD.

There are lots of frameworks in the Java community that support BDD concepts. Citrus has dedicated support for the Cucumber framework because Cucumber is well suited for extensions and plugins. So with the Citrus and Cucumber integration you can write Gherkin syntax scenario and feature stories in order to execute the Citrus integration test capabilities. As usual we have a look at a first example. First lets see the Citrus cucumber dependency and XML schema definitions.

**Note**
The Cucumber components in Citrus are kept in a separate Maven module. If not already done so you have to include the module as Maven dependency to your project

```xml
<dependency>
  <groupId>com.consol.citrus</groupId>
  <artifactId>citrus-cucumber</artifactId>
  <version>2.7.2-SNAPSHOT</version>
</dependency>
```

Citrus provides a separate configuration namespace and schema definition for Cucumber related step definitions. Include this namespace into your Spring configuration in order to use the Citrus Cucumber configuration elements. The namespace URI and schema location are added to the Spring configuration XML file as follows.

```xml
<spring:beans xmlns:spring="http://www.springframework.org/schema/beans"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xmlns="http://www.citrusframework.org/schema/cucumber/testcase"
     xsi:schemaLocation="
     http://www.springframework.org/schema/beans
     http://www.springframework.org/schema/beans/spring-beans.xsd
     http://www.citrusframework.org/schema/cucumber/testcase
     http://www.citrusframework.org/schema/cucumber/testcase/citrus-cucumber-testcase.xsd">

    [...]

</spring:beans>
```

Cucumber works with both JUnit and TestNG as unit testing framework. You can choose which framework to use with Cucumber. So following from that we need a Maven dependency for the unit testing framework support:

```xml
<dependency>
  <groupId>info.cukes</groupId>
  <artifactId>cucumber-junit</artifactId>
  <version>${cucumber.version}</version>
</dependency>
```

In order to enable Citrus Cucumber support we need to specify a special object factory in the environment. The most comfortable way to specify a custom object factory is to add this property to the **cucumber.properties** in classpath.

```xml
cucumber.api.java.ObjectFactory=cucumber.runtime.java.CitrusObjectFactory
```

This special object factory takes care on creating all step definition instances. The object factory is able to inject **@CitrusResource** annotated fields in step classes. We will see this later on in the examples. The usage of this special object factory is mandatory in order to combine Citrus and Cucumber capabilities.

The **CitrusObjectFactory** will automatically initialize the Citrus world for us. This includes the default **citrus-context.xml** Citrus Spring configuration that is automatically loaded within the object factory. So you can define and use Citrus components as usual within your test.

After these preparation steps you are able to combine Citrus and Cucumber in your project.

### Cucumber integration

Cucumber is able to run tests with JUnit. The basic test case is an empty test which uses the respective JUnit runner implementation from cucumber.

```java
@RunWith(Cucumber.class)
@CucumberOptions(
  plugin = { "com.consol.citrus.cucumber.CitrusReporter" } )
public class MyFeatureIT {

}
```

The test case above uses the **Cucumber** JUnit test runner. In addition to that we give some options to the Cucumber execution. We define a special Citrus reporter implementation. This class is responsible for printing the Citrus test summary. This reporter extends the default Cucumber reporter implementation so the default Cucumber report summaries are also printed to the console.

That completes the JUnit class configuration. Now we are able to add feature stories and step definitions to the package of our test **MyFeatureIT** . Cucumber and Citrus will automatically pick up step definitions and glue code in that test package. So lets write a feature story **echo.feature** right next to the **MyFeatureIT** test class.

```xml
Feature: Echo service

  Scenario: Say hello
    Given My name is Citrus
    When I say hello to the service
    Then the service should return: "Hello, my name is Citrus!"

  Scenario: Say goodbye
    Given My name is Citrus
    When I say goodbye to the service
    Then the service should return: "Goodbye from Citrus!"
```

As you can see this story defines two scenarios with the Gherkin **Given-When-Then** syntax. Now we need to add step definitions that glue the story description to Citrus test actions. Lets do this in a new class **EchoSteps** .

```java
public class EchoSteps {

    @CitrusResource
    protected TestDesigner designer;

    @Given("^My name is (.*)$")
    public void my_name_is(String name) {
        designer.variable("username", name);
    }

    @When("^I say hello.*$")
    public void say_hello() {
        designer.send("echoEndpoint")
          .messageType(MessageType.PLAINTEXT)
          .payload("Hello, my name is ${username}!");
    }

    @When("^I say goodbye.*$")
    public void say_goodbye() {
        designer.send("echoEndpoint")
          .messageType(MessageType.PLAINTEXT)
          .payload("Goodbye from ${username}!");
    }

    @Then("^the service should return: \"([^\"]*)\"$")
    public void verify_return(final String body) {
        designer.receive("echoEndpoint")
          .messageType(MessageType.PLAINTEXT)
          .payload("You just said: " + body);
    }

}
```

If we have a closer look at the step definition class we see that it is a normal POJO that uses a **@CitrusResource** annotated **TestDesigner**. The test designer is automatically injected by Citrus Cucumber extension. This is done because we have included the citrus-cucumber dependency to our project before. Now we can write @Given, @When or @Then annotated methods that match the scenario descriptions in our story. Cucumber will automatically find matching methods and execute them. The methods add test actions to the test designer as we are used to it in normal Java DSL tests. At the end the test designer is automatically executed with the test logic.

**Important**
Of course you can do the dependency injection with **@CitrusResource** annotations on **TestRunner** instances, too. Which variation should someone use **TestDesigner** or **TestRunner** ? In fact there is a significant difference when looking at the two approaches. The designer will use the Gherkin methods to build the whole Citrus test case first before any action is executed. The runner will execute each test action that has been built with a Gherkin step immediately. This means that a designer approach will always complete all BDD step definitions before taking action. This directly affects the Cucumber step reports. All steps are usually marked as successful when using a designer approach as the Citrus test is executed after the Cucumber steps have been executed. The runner approach in contrast will fail the step when the corresponding test action fails. The Cucumber test reports will definitely look different depending on what approach you are choosing. All other functions stay the same in both approaches. If you need to learn more about designer and runner approaches please see

If we run the Cucumber test the Citrus test case automatically performs its actions. That is a first combination of Citrus and Cucumber BDD. The story descriptions are translated to test actions and we are able to run integration tests with behavior driven development. Great! In a next step we will use XML step definitions rather than coding the steps in Java DSL.

### Cucumber XML steps

So far we have written glue code in Java in order to translate Gherkin syntax descriptions to test actions. Now we want to do the same with just XML configuration. The JUnit Cucumber class should not change. We still use the Cucumber runner implementation with some options specific to Citrus:

```java
@RunWith(Cucumber.class)
@CucumberOptions(
    plugin = { "com.consol.citrus.cucumber.CitrusReporter" } )
public class MyFeatureIT {

}
```

The scenario description is also not changed:

```xml
Feature: Echo service

  Scenario: Say hello
    Given My name is Citrus
    When I say hello to the service
    Then the service should return: "Hello, my name is Citrus!"

  Scenario: Say goodbye
    Given My name is Citrus
    When I say goodbye to the service
    Then the service should return: "Goodbye from Citrus!"
```

In the feature package **my.company.features** we add a new XML file **EchoSteps.xml** that holds the new XML step definitions:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<spring:beans xmlns:citrus="http://www.citrusframework.org/schema/testcase"
      xmlns:spring="http://www.springframework.org/schema/beans"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns="http://www.citrusframework.org/schema/cucumber/testcase"
      xsi:schemaLocation="http://www.springframework.org/schema/beans
                          http://www.springframework.org/schema/beans/spring-beans.xsd
                          http://www.citrusframework.org/schema/cucumber/testcase
                          http://www.citrusframework.org/schema/cucumber/testcase/citrus-cucumber-testcase.xsd">

  <step given="^My name is (.*)$" parameter-names="username">
    <citrus:create-variables>
      <citrus:variable name="username" value="${username}"/>
    </citrus:create-variables>
  </step>

  <step when="^I say hello.*$">
    <citrus:send endpoint="echoEndpoint">
      <citrus:message type="plaintext">
        <citrus:data>Hello, my name is ${username}!</citrus:data>
      </citrus:message>
    </citrus:send>
  </step>

  <step when="^I say goodbye.*$">
    <citrus:send endpoint="echoEndpoint">
      <citrus:message type="plaintext">
        <citrus:data>Goodbye from ${username}!</citrus:data>
      </citrus:message>
    </citrus:send>
  </step>

  <step then="^the service should return: &quot;([^&quot;]*)&quot;$" parameter-names="body">
    <citrus:receive endpoint="echoEndpoint">
      <citrus:message type="plaintext">
        <citrus:data>You just said: ${body}</citrus:data>
      </citrus:message>
    </citrus:receive>
  </step>

</spring:beans>
```

The above steps definition is written in pure XML. Citrus will automatically read the step definition and add those to the Cucumber runtime. Following from that the step definitions are executed when matching to the feature story. The XML step files follow a naming convention. Citrus will look for all files located in the feature package with name pattern ****/**.Steps.xml** and load those definitions when Cucumber starts up.

The XML steps are able to receive parameters from the Gherkin regexp matcher. The parameters are passed to the step as test variable. The parameter names get declared in the optional attribute **parameter-names** . In the step definition actions you can use the parameter names as test variables.

**Note**
The test variables are visible in all upcoming steps, too. This is because the test variables are global by default. If you need to set local state for a step definition you can use another attribute **global-context** and set it to **false** in the step definition. This way all test variables and parameters are only visible in the step definition. Other steps will not see the test variables.

**Note**
Another notable thing is the XML escaping of reserved characters in the pattern definition. You can see that in the last step where the **then** attribute is escaping quotation characters.

```xml
then="^the service should return: &quot;([^&quot;]*)&quot;$"
```

We have to do this because otherwise the quotation characters will interfere with the XML syntax in the attribute.

This completes the description of how to add XML step definitions to the cucumber BDD tests. In a next section we will use predefined steps for sending and receiving messages.

### Cucumber Spring support

Cucumber provides support for Spring dependency injection in step definition classes. The Cucumber Spring capabilities are included in a separate module. So we first of all we have to add this dependency to our project:

```xml
<dependency>
  <groupId>info.cukes</groupId>
  <artifactId>cucumber-spring</artifactId>
  <version>${cucumber.version}</version>
</dependency>
```

The Citrus Cucumber extension has to handle things different when Cucumber Spring support is enabled. Therefore we use another object factory implementation that also support Cucumber Spring features. Change the object factory property in **cucumber.properties** to the following:

```xml
cucumber.api.java.ObjectFactory=cucumber.runtime.java.spring.CitrusSpringObjectFactory
```

Now we are ready to add **@Autowired** Spring bean dependeny injection to step definition classes:

```java
@ContextConfiguration(classes = CitrusSpringConfig.class)
public class EchoSteps {
    @Autowired
    private Endpoint echoEndpoint;

    @CitrusResource
    protected TestDesigner designer;

    @Given("^My name is (.*)$")
    public void my_name_is(String name) {
        designer.variable("username", name);
    }

    @When("^I say hello.*$")
    public void say_hello() {
        designer.send(echoEndpoint)
            .messageType(MessageType.PLAINTEXT)
            .payload("Hello, my name is ${username}!");
    }

    @When("^I say goodbye.*$")
    public void say_goodbye() {
        designer.send(echoEndpoint)
            .messageType(MessageType.PLAINTEXT)
            .payload("Goodbye from ${username}!");
    }

    @Then("^the service should return: \"([^\"]*)\"$")
    public void verify_return(final String body) {
        designer.receive(echoEndpoint)
            .messageType(MessageType.PLAINTEXT)
            .payload("You just said: " + body);
    }
}
```

As you can see we used Spring autowiring mechanism for the **echoEndpoint** field in the step definition. Also be sure to define the **@ContextConfiguration** annotation on the step definition. The Cucumber Spring support loads the Spring application context and takes care on dependency injection. We use the Citrus **CitrusSpringConfig** Java configuration because this is the main entrance for Citrus test cases. You can add custom beans and further Spring related configuration to this Spring application context. If you want to add more beans for autowiring do so in the Citrus Spring configuration. Usually this is the default **citrus-context.xml** which is automatically loaded.

Of course you can also use a custom Java Spring configuration class here. But be sure to always import the Citrus Spring Java configuration classes, too. Otherwise you will not be able to execute the Citrus integration test capabilities.

As usual we are able to use **@CitrusResource** annotated **TestDesigner** fields for building the Citrus integration test logic. With this extension you can use the full Spring testing power in your tests in particular dependency injection and also transaction management for data persistance tests.

### Citrus step definitions

Citrus provides some out of the box predefined steps for typical integration test scenarios. These steps are ready to use in scenario or feature stories. You can basically define send and receive operations. As these steps are predefined in Citrus you just need to write feature stories. The step definitions with glue to test actions are handled automatically.

If you want to enable predefined steps support in your test you need to include the glue code package in your test class like this:

```java
@RunWith(Cucumber.class)
@CucumberOptions(
    glue = { "com.consol.citrus.cucumber.step.designer.core" },
    plugin = { "com.consol.citrus.cucumber.CitrusReporter" } )
public class MyFeatureIT {

}
```

Instead of writing the glue code on our own in step definition classes we include the glue package **com.consol.citrus.cucumber.step.designer.core** . This automatically loads all Citrus glue step definitions in this package. Once you have done this you can use predefined steps that add Citrus test logic without having to write any glue code in Java step definitions.

Of course you can also choose to include the **TestRunner** step definitions by choosing the glue package **com.consol.citrus.cucumber.step.runner.core** .

```java
@RunWith(Cucumber.class)
@CucumberOptions(
    glue = { "com.consol.citrus.cucumber.step.runner.core" },
    plugin = { "com.consol.citrus.cucumber.CitrusReporter" } )
public class MyFeatureIT {

}
```

Following basic step definitions are included in this package:

```xml
Given variable [name] is "[value]"
Given variables
| [name1]   | [value1]   |
| [name2]   | [value2]   |

When <[endpoint-name]> sends "[message-payload]"
Then <[endpoint-name]> should receive (message-type) "[message-payload]"

When <[endpoint-name]> sends
  """
  [message-payload]
  """
Then <[endpoint-name]> should receive (message-type)
  """
  [message-payload]
  """

When <[endpoint-name]> receives (message-type) "[message-payload]"
Then <[endpoint-name]> should send "[message-payload]"

When <[endpoint-name]> receives (message-type)
  """
  [message-payload]
  """
Then <[endpoint-name]> should send
  """
  [message-payload]
  """
```

Once again it should be said that the step definitions included in this package are loaded automatically as glue code. So you can start to write feature stories in Gherkin syntax that trigger the predefined steps. 

There are several default step definitions for different aspects of integration testing. Please see the following packages that define default steps in Citrus:

**Test designer packages**

* com.consol.citrus.cucumber.step.designer.core
* com.consol.citrus.cucumber.step.designer.http
* com.consol.citrus.cucumber.step.designer.docker
* com.consol.citrus.cucumber.step.designer.selenium

**Test runner packages**

* com.consol.citrus.cucumber.step.runner.core
* com.consol.citrus.cucumber.step.runner.http
* com.consol.citrus.cucumber.step.runner.docker
* com.consol.citrus.cucumber.step.runner.selenium

In the following sections we have a closer look at all predefined Citrus steps and how they work.

### Variable steps

As you already know Citrus is able to work with test variables that hold important information during a test such as identifiers and dynamic values. The predefined step definitions in Citrus are able to create new test variables.

```xml
Given variable messageText is "Hello"
```

The syntax of this predefined step is pretty self describing. The step instruction follows the pattern:

```xml
Given variable [name] is "[value]"
```

If you keep this syntax in your feature story the predefined step is activated for creating a new variable. We always use the **Given** step to create new variables.

```xml
Scenario: Create Variables
    Given variable messageText is "Hello"
      And variable operationHeader is "sayHello"
```

So we can use the **And** keyword to create more than one variable. Even more comfortable is the usage of data tables:

```xml
Given variables
    | hello   | I say hello   |
    | goodbye | I say goodbye |
```

This data table will create the test variable for each row. This is how you can easily create new variables in your Citrus test. As usual the variables are referenced in message payloads and headers as placeholders for dynamically adding content.

Adding variables is usually done within a **Scenario** block in your feature story. This means that the test variable is used in this scenario which is exactly one Citrus test case. Cucumber BDD also defines a **Background** block at the very beginning of your **Feature** . We can also place variables in here. This means that Cucumber will execute these steps for all upcoming scenarios. The test variable is so to speak global for this feature story.

```xml
Feature: Variables

    Background:
      Given variable messageText is "Hello"

    Scenario: Do something
    Scenario: Do something else
```

That completes the variable step definitions in Citrus.

### Messaging steps

In the previous section we have learned how to use a first predefined Citrus step. Now we want to cover messaging steps for sending and receiving messages in Citrus. As usual with predefined steps you do not need to write any glue code for the steps to take action. The steps are already included in Citrus just use them in your feature stories.

```xml
Feature: Messaging features

    Background:
      Given variable messageText is "Hello"

    Scenario: Send and receive plaintext
      When <echoEndpoint> sends "${messageText}"
      Then <echoEndpoint> should receive plaintext "You just said: ${messageText}"
```

Of course we need to follow the predefined syntax when writing feature stories in order to trigger a predefined step. Let's have a closer look at this predefined syntax by further describing the above example.

First of all we define a new test variable with **Given variable messageText is "Hello"** . This tells Citrus to create a new test variable named **messageText** with respective value. We can do the same for sending and receiving messages like done in our test scenario:

```xml
When <[endpoint-name]> sends "[message-payload]"
```

The step definition requires the endpoint component name and a message payload. The predefined step will automatically configure a send test action in the Citrus test as result.

```xml
Then <[endpoint-name]> should receive (message-type) "[message-payload]"
```

The predefined receive step also requires the **endpoint-name** and **message-payload** . As optional parameter you can define the **message-type** . This is required when sending message payloads other than XML.

This way you can write Citrus tests with just writing feature stories in Gherkin syntax. Up to now we have used pretty simple message payloads in on single line. Of course we can also use multiline payloads in the stories:

```xml
Feature: Messaging features

    Background:
      Given variable messageText is "Hello"

    Scenario: Send and receive
      When <echoEndpoint> sends
        """
        <message>
          <text>${messageText}</text>
        </message>
        """
      Then <echoEndpoint> should receive
        """
        <message>
          <text>${messageText}</text>
        </message>
        """
```

As you can see we are able to use the send and receive steps with multiline XML message payload data.

### Named messages

In the previous section we have learned how to use Citrus predefined step definitions for send and receive operations. The message payload has been added directly to the stories so far. But what is with message header information? We want to specify a complete message with payload and header. You can do this by defining a named message.

As usual we demonstrate this in a first example:

```xml
Feature: Named message feature

    Background:
      Given message echoRequest
        And <echoRequest> payload is "Hi my name is Citrus!"
        And <echoRequest> header operation is "sayHello"

      Given message echoResponse
        And <echoResponse> payload is "Hi, Citrus how are you doing today?"
        And <echoResponse> header operation is "sayHello"

    Scenario: Send and receive
      When <echoEndpoint> sends message <echoRequest>
      Then <echoEndpoint> should receive message <echoResponse>
```

In the **Background** section we introduce named messages **echoRequest** and **echoResponse** . This makes use of the new predefined step for adding named message:

```xml
Given message [message-name]
```

Once the message is introduced with its name we can use the message in further configuration steps. You can add payload information and you can add multiple headers to the message. The named message then is referenced in send and receive steps as follows:

```xml
When <[endpoint-name]> sends message <[message-name]>
Then <[endpoint-name]> should receive message <[message-name]>
```

The steps reference a message by its name **echoRequest** and **echoResponse** .

As you can see the named messages are used to define complete messages with payload and header information. Of course the named messages can be referenced in many scenarios and steps. Also with usage of test variables in payload and header you can dynmaically adjust those messages in each step.

### Message creator steps

In the previous section we have learned how to use named messages as predefined step. The named message has been defined directly in the stories so far. The message creator concept moves this task to some Java POJO. This way you are able to construct more complicated messages for reuse in several scenarios and feature stories.

As usual we demonstrate this in a first example:

```xml
Feature: Message creator features

    Background:
      Given message creator com.consol.citrus.EchoMessageCreator
      And variable messageText is "Hello"
      And variable operation is "sayHello"

    Scenario: Send and receive
      When <echoEndpoint> sends message <echoRequest>
      Then <echoEndpoint> should receive message <echoResponse>
```

In the **Background** section we introduce a message creator **EchoMessageCreator** in package **com.consol.citrus** . This makes use of the new predefined step for adding message creators to the test:

```xml
Given message creator [message-creator-name]
```

The message creator name must be the fully qualified Java class name with package information. Once this is done we can use named messages in the send and receive operations:

```xml
When <[endpoint-name]> sends message <[message-name]>
Then <[endpoint-name]> should receive message <[message-name]>
```

The steps reference a message by its name **echoRequest** and **echoResponse** . Now lets have a look at the message creator **EchoMessageCreator** implementation in order to see how this correlates to a real message.

```java
public class EchoMessageCreator {
    @MessageCreator("echoRequest")
    public Message createEchoRequest() {
      return new DefaultMessage("" +
            "${messageText}" +
          "")
            .setHeader("operation", "${operation}");
    }

    @MessageCreator("echoResponse")
    public Message createEchoResponse() {
      return new DefaultMessage("" +
            "${messageText}" +
          "")
            .setHeader("operation", "${operation}");
    }
}
```

As you can see the message creator is a POJO Java class that defines one or more methods that are annotated with **@MessageCreator** annotation. The annotation requires a message name. This is how Citrus will correlate message names in feature stories to message creator methods. The message returned is the used for the send and receive operations in the test. The message creator is reusable accross multiple feature stories and scenarios. In addition to that the creator is able to construct messages in a more powerful way. For instance the message payload could be loaded from file system resources.

### Echo steps

Another predefined step definition in Citrus is used to add a **echo** test action. You can use the following step in your feature scenarios:

```xml
Feature: Echo features

    Scenario: Echo messages
      Given variable foo is "bar"
      Then echo "Variable foo=${foo}"
      Then echo "Today is citrus:currentDate()"
```

The step definition requires following pattern:

```xml
Then echo "[message]"
```

### Sleep steps

You can add **sleep** test actions to the feature scenarios:

```xml
Feature: Sleep features

      Scenario: Sleep default time
        Then sleep

      Scenario: Sleep milliseconds time
        Then sleep 200 ms
```

The step definition requires one of the following patterns:

```xml
Then sleep
Then sleep [time] ms
```

This adds a new sleep test action to the Citrus test.

### Http steps

The Http steps are specially designed for Http client-server communication. You can use these steps by adding following packages as glue options in your Cucumber test:

* com.consol.citrus.cucumber.step.(designer|runner).http

This package contains Http specific steps that enable you to send and receive messages via Http REST:

```
Feature: Voting Http REST API

  Background:
    Given URL: http://localhost:8080/rest/services
    Given variables
      | id      | citrus:randomUUID()  |
      | title   | Do you like Mondays? |
      | options | [ { "name": "yes", "votes": 0 }, { "name": "no", "votes": 0 } ] |
      | report  | true                 |

  Scenario: Clear voting list
    When send DELETE /voting
    Then receive status 200 OK

  Scenario: Get empty voting list
    Given Accept: application/json
    When send GET /voting
    Then Response: []
    And receive status 200 OK

  Scenario: Create voting
    Given Request:
    """
    {
      "id": "${id}",
      "title": "${title}",
      "options": ${options},
      "report": ${report}
    }
    """
    And Content-Type: application/json
    When send POST /voting
    Then receive status 200 OK

  Scenario: Get voting list
    When send GET /voting
    Then validate $.size() is 1
    Then validate $..title is ${title}
    Then validate $..report is ${report}
    And receive status 200 OK
```

The feature scenarios use default Http steps to send requests with different methods (GET, POST, PUT, DELETE) and receive status responses (Http 200 OK). Please
explore the default step definitions in the respective package to get a detailed understanding on how to use those in your feature specification.

### Docker steps

Docker steps access containers and build images. By default the steps try to find a valida DockerClient component in the Spring application context configuration.
You can use the steps in feature specifications to manage container states.

```
Feature: Voting Docker infrastructure

  Scenario: Check container deployment state
    Given docker-client "dockerClient"
    Then container "voting-app" should be running
    And container "message-broker" should be running
```

We are able to check the container state `running`. All we need is the Docker container name or id. What else can we do within the default Docker steps? We can
build new images:

```
Feature: Build images

  Scenario: Build voting image
    Given docker-client "dockerClient"
    When build image "voting:1.0.0" from file "scr/main/docker/Dockerfile"
    Then create container "voting-app" from "voting:1.0.0"
    And container "voting-app" should be running
```

This is how we can use Docker commands in Cucumber feature specifications with Citrus default step definitions. All default step definitions for Docker are located in package

* com.consol.citrus.cucumber.step.(designer|runner).docker

### Selenium steps

Selenium is a widely used UI automation framework where browser user interactions are simulated. We can use default Selenium steps in the feature specifications in order to
access Selenium commands in our tests.

```
Feature: Voting user interface

  Background:
    Given user starts browser
    And user navigates to "http://localhost:8080"

  Scenario: Welcome page
    Then page should display link with link-text="Run application"

  Scenario: Start application
    When user clicks link with link-text="Run application"
    And sleep 500 ms
    Then page should display heading with tag-name="h1" having
    | text | Voting list |

    And page should display link with link-text="No voting found"
    And page should display form with id="new-voting" having
    | tag-name  | form          |
    | attribute | method="post" |

  Scenario: Add voting
    Given user navigates to "http://localhost:8080/voting"
    When user sets text "Do you like burgers?" to input with id="title"
    And user clicks button with id="submitNew"
    And sleep 500 ms
    Then page should display element with link-text="Do you like burgers?"
```

With the predefined Cucumber steps for Selenium we are able to interact with the browser. For instance we can click buttons, verify page objects and
navigate to different pages.

All these Selenium steps are located in package:

* com.consol.citrus.cucumber.step.(designer|runner).selenium

The Selenium browser is automatically picked from the Spring bean application context configuration in Citrus. Here you can decide which Selenium WebDriver to use during the tests.
Also you can instantiate web page instances and call page actions and validation steps:

```java
public class VotingListPage implements WebPage, PageValidator<VotingListPage> {

    @FindBy(tagName = "h1")
    private WebElement heading;

    @FindBy(id = "new-voting")
    private WebElement newVotingForm;

    /**
     * Submits new voting.
     * @param title
     * @param options
     */
    public void submit(String title, String options) {
        newVotingForm.findElement(By.id("title")).sendKeys(title);
        if (StringUtils.hasText(options)) {
            newVotingForm.findElement(By.id("options")).sendKeys(options.replaceAll(":", "\n"));
        }

        newVotingForm.submit();
    }

    @Override
    public void validate(VotingListPage webPage, SeleniumBrowser browser, TestContext context) {
        Assert.assertEquals("Voting list", heading.getText());
    }
}
```

This page object defines elements and actions on that page that are callable in our feature specification.

```
Feature: Voting pages

  Background:
    Given page "welcomePage" com.consol.citrus.demo.voting.selenium.pages.WelcomePage
    Given page "votingListPage" com.consol.citrus.demo.voting.selenium.pages.VotingListPage

  Scenario: Welcome page
    When user starts browser
    And user navigates to "http://localhost:8080"
    Then page welcomePage should validate

  Scenario: Start application
    When user navigates to "http://localhost:8080"
    And page welcomePage performs startApp
    And sleep 500 ms
    Then page votingListPage should validate

  Scenario: Add voting
    Given user navigates to "http://localhost:8080/voting"
    When page votingListPage performs submit with arguments
    | Do you like pizza? |
    And sleep 500 ms
    Then page should display element with link-text="Do you like pizza?"
    And page votingListPage should validate

  Scenario: Add voting with options
    Given user navigates to "http://localhost:8080/voting"
    When page votingListPage performs submit with arguments
      | What is your favorite color? |
      | red:green:blue |
    And sleep 500 ms
    Then page should display element with link-text="What is your favorite color?"
    And page votingListPage should validate
```

The page objects get instantiated and dependency injection makes sure that web elements and other resources are passed to 
the page object. Then action method can perform as well as validation tasks can validate the page state.
