---
layout: sample
title: SOAP WS Security sample
sample: sample-soap-wssecurity
description: Configure SOAP web service client and server with WSSecurity
categories: [samples]
permalink: /samples/soap-wssecurity/
---

This sample uses SOAP web services with WSSecurity username token authentication. Clients must authenticate with proper wsse security elements and username
password. You can read more about the Citrus SOAP features in [reference guide](http://www.citrusframework.org/reference/html/soap.html)

Objectives
---------

The sample project uses both client and server components to demonstrate WSSecurity configuration. The Citrus SOAP web service
server endpoint validates incoming requests using the Spring **Wss4jSecurityInterceptor**.

First of all we add the dependency **spring-ws-security** to the Maven POM.

{% highlight xml %}
<dependency>
  <groupId>org.springframework.ws</groupId>
  <artifactId>spring-ws-security</artifactId>
  <version>${spring.ws.version}</version>
  <scope>test</scope>
</dependency>
{% endhighlight %}
    
After that we can configure the SOAP client to use WSS4J security username and password for all requests.

{% highlight xml %}
<citrus-ws:client id="todoListClient"
                  request-url="http://localhost:8080/services/ws/todolist"
                  interceptors="clientInterceptors"/>

<util:list id="clientInterceptors">
  <bean class="org.springframework.ws.soap.security.wss4j.Wss4jSecurityInterceptor">
    <property name="securementActions" value="Timestamp UsernameToken"/>
    <property name="securementUsername" value="admin"/>
    <property name="securementPassword" value="secret"/>
  </bean>
  <bean class="com.consol.citrus.ws.interceptor.LoggingClientInterceptor"/>
</util:list>
{% endhighlight %}
   
The client interceptor list contains the **Wss4jSecurityInterceptor** security interceptor that automatically adds username and password
tokens in the SOAP header.

The server component has to verify incoming requests to have this token set as expected:

{% highlight xml %}
<citrus-ws:server id="todoListServer"
                  port="8080"
                  auto-start="true"
                  interceptors="serverInterceptors"/>

<util:list id="serverInterceptors">
  <bean class="com.consol.citrus.ws.interceptor.SoapMustUnderstandEndpointInterceptor">
    <property name="acceptedHeaders">
      <list>
        <value>{http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd}Security</value>
      </list>
    </property>
  </bean>
  <bean class="com.consol.citrus.ws.interceptor.LoggingEndpointInterceptor"/>
  <bean class="org.springframework.ws.soap.security.wss4j.Wss4jSecurityInterceptor">
    <property name="validationActions" value="Timestamp UsernameToken"/>
    <property name="validationCallbackHandler">
      <bean id="passwordCallbackHandler" class="org.springframework.ws.soap.security.wss4j.callback.SimplePasswordValidationCallbackHandler">
        <property name="usersMap">
          <map>
            <entry key="admin" value="secret"/>
          </map>
        </property>
      </bean>
    </property>
  </bean>
</util:list>   
{% endhighlight %}
     
The server security interceptor validates with simple username password handler. As a result only granted users can access
the web services. 

When you execute the test client and server operations will send and receive messages with WSSecurity enabled. You will see the security headers
added to the SOAP message headers accordingly.

{% highlight xml %}
<SOAP-ENV:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" SOAP-ENV:mustUnderstand="1" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
        <wsse:UsernameToken wsu:Id="UsernameToken-C3539350EAFCFDFD3D14792492533112">
            <wsse:Username>admin</wsse:Username>
            <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">HyT/oOdQfy6liZxqEO05gA9sqjU=</wsse:Password>
            <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">oumOQpmSCjw9bc5yw4qgLQ==</wsse:Nonce>
            <wsu:Created>2016-11-15T22:34:13.311Z</wsu:Created>
        </wsse:UsernameToken>
        <wsu:Timestamp wsu:Id="TS-C3539350EAFCFDFD3D14792492532881">
            <wsu:Created>2016-11-15T22:34:13.031Z</wsu:Created>
            <wsu:Expires>2016-11-15T22:39:13.031Z</wsu:Expires>
        </wsu:Timestamp>
    </wsse:Security>
</SOAP-ENV:Header>
{% endhighlight %}
        
Run
---------

The sample application uses Maven as build tool. So you can compile, package and test the
sample with Maven.
 
     mvn clean install
    
This executes the complete Maven build lifecycle. During the build you will see Citrus performing some integration tests.

Execute all Citrus tests by calling

     mvn integration-test

You can also pick a single test by calling

     mvn integration-test -Ptest=TodoListIT

You should see Citrus performing several tests with lots of debugging output. 
And of course green tests at the very end of the build.

Of course you can also start the Citrus tests from your favorite IDE.
Just start the Citrus test using the TestNG IDE integration in IntelliJ, Eclipse or Netbeans.