/*
 * Copyright 2006-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.jms.endpoint;

import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.ActionTimeoutException;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.message.Message;
import com.consol.citrus.message.correlation.CorrelationManager;
import com.consol.citrus.message.correlation.PollingCorrelationManager;
import com.consol.citrus.messaging.ReplyConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.ConnectionFactoryUtils;
import org.springframework.jms.support.JmsUtils;
import org.springframework.jms.support.destination.DynamicDestinationResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.jms.*;

/**
 * @author Christoph Deppisch
 * @since 1.4
 */
public class JmsSyncProducer extends JmsProducer implements ReplyConsumer {

    /** JMS connection */
    private Connection connection = null;

    /** JMS session */
    private Session session = null;

    /** Store of reply messages */
    private CorrelationManager<Message> correlationManager;

    /** Endpoint configuration */
    private final JmsSyncEndpointConfiguration endpointConfiguration;

    /** Logger */
    private static Logger log = LoggerFactory.getLogger(JmsSyncProducer.class);

    /**
     * Default constructor using endpoint configuration.
     * @param name
     * @param endpointConfiguration
     */
    public JmsSyncProducer(String name, JmsSyncEndpointConfiguration endpointConfiguration) {
        super(name, endpointConfiguration);
        this.endpointConfiguration = endpointConfiguration;

        this.correlationManager = new PollingCorrelationManager(endpointConfiguration, "Reply message did not arrive yet");
    }

    @Override
    public void send(Message message, TestContext context) {
        Assert.notNull(message, "Message is empty - unable to send empty message");

        String correlationKeyName = endpointConfiguration.getCorrelator().getCorrelationKeyName(getName());
        String correlationKey = endpointConfiguration.getCorrelator().getCorrelationKey(message);
        correlationManager.saveCorrelationKey(correlationKeyName, correlationKey, context);
        String defaultDestinationName = endpointConfiguration.getDefaultDestinationName();

        if (log.isDebugEnabled()) {
            log.debug("Sending JMS message to destination: '" + defaultDestinationName + "'");
        }

        context.onOutboundMessage(message);

        MessageProducer messageProducer = null;
        MessageConsumer messageConsumer = null;
        Destination replyToDestination = null;

        try {
            createConnection();
            createSession(connection);

            javax.jms.Message jmsRequest = endpointConfiguration.getMessageConverter().createJmsMessage(message, session, endpointConfiguration, context);
            endpointConfiguration.getMessageConverter().convertOutbound(jmsRequest, message, endpointConfiguration, context);

            messageProducer = session.createProducer(getDefaultDestination(session));

            replyToDestination = getReplyDestination(session, message);
            if (replyToDestination instanceof TemporaryQueue || replyToDestination instanceof TemporaryTopic) {
                messageConsumer = session.createConsumer(replyToDestination);
            }

            jmsRequest.setJMSReplyTo(replyToDestination);
            messageProducer.send(jmsRequest);

            if (messageConsumer == null) {
                messageConsumer = createMessageConsumer(replyToDestination, jmsRequest.getJMSMessageID());
            }

            log.info("Message was sent to JMS destination: '{}'", defaultDestinationName);
            log.debug("Receiving reply message on destination: '{}'", replyToDestination);

            javax.jms.Message jmsReplyMessage = (endpointConfiguration.getTimeout() >= 0) ? messageConsumer.receive(endpointConfiguration.getTimeout()) : messageConsumer.receive();

            if (jmsReplyMessage == null) {
                throw new ActionTimeoutException("Reply timed out after " +
                        endpointConfiguration.getTimeout() + "ms. Did not receive reply message on reply destination");
            }

            Message responseMessage = endpointConfiguration.getMessageConverter().convertInbound(jmsReplyMessage, endpointConfiguration, context);

            log.info("Received reply message on JMS destination: '{}'", replyToDestination);

            context.onInboundMessage(responseMessage);

            correlationManager.store(correlationKey, responseMessage);
        } catch (JMSException e) {
            throw new CitrusRuntimeException(e);
        } finally {
            JmsUtils.closeMessageProducer(messageProducer);
            JmsUtils.closeMessageConsumer(messageConsumer);
            deleteTemporaryDestination(replyToDestination);
        }
    }

    @Override
    public Message receive(TestContext context) {
        return receive(correlationManager.getCorrelationKey(
                endpointConfiguration.getCorrelator().getCorrelationKeyName(getName()), context), context);
    }

    @Override
    public Message receive(String selector, TestContext context) {
        return receive(selector, context, endpointConfiguration.getTimeout());
    }

    @Override
    public Message receive(TestContext context, long timeout) {
        return receive(correlationManager.getCorrelationKey(
                endpointConfiguration.getCorrelator().getCorrelationKeyName(getName()), context), context, timeout);
    }

    @Override
    public Message receive(String selector, TestContext context, long timeout) {
        Message message = correlationManager.find(selector, timeout);

        if (message == null) {
            throw new ActionTimeoutException("Action timeout while receiving synchronous reply message on jms destination");
        }

        return message;
    }

    /**
     * Create new JMS connection.
     * @return connection
     * @throws JMSException
     */
    protected void createConnection() throws JMSException {
        if (connection == null) {
            if (!endpointConfiguration.isPubSubDomain() && endpointConfiguration.getConnectionFactory() instanceof QueueConnectionFactory) {
                connection = ((QueueConnectionFactory) endpointConfiguration.getConnectionFactory()).createQueueConnection();
            } else if (endpointConfiguration.isPubSubDomain() && endpointConfiguration.getConnectionFactory() instanceof TopicConnectionFactory) {
                connection = ((TopicConnectionFactory) endpointConfiguration.getConnectionFactory()).createTopicConnection();
                connection.setClientID(getName());
            } else {
                log.warn("Not able to create a connection with connection factory '" + endpointConfiguration.getConnectionFactory() + "'" +
                        " when using setting 'publish-subscribe-domain' (=" + endpointConfiguration.isPubSubDomain() + ")");

                connection = endpointConfiguration.getConnectionFactory().createConnection();
            }

            connection.start();
        }
    }

    /**
     * Create new JMS session.
     * @param connection to use for session creation.
     * @return session.
     * @throws JMSException
     */
    protected void createSession(Connection connection) throws JMSException {
        if (session == null) {
            if (!endpointConfiguration.isPubSubDomain() && connection instanceof QueueConnection) {
                session = ((QueueConnection) connection).createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            } else if (endpointConfiguration.isPubSubDomain() && endpointConfiguration.getConnectionFactory() instanceof TopicConnectionFactory) {
                session = ((TopicConnection) connection).createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            } else {
                log.warn("Not able to create a session with connection factory '" + endpointConfiguration.getConnectionFactory() + "'" +
                        " when using setting 'publish-subscribe-domain' (=" + endpointConfiguration.isPubSubDomain() + ")");

                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }
        }
    }

    /**
     * Creates a message consumer on temporary/durable queue or topic. Durable queue/topic destinations
     * require a message selector to be set.
     *
     * @param replyToDestination the reply destination.
     * @param messageId the messageId used for optional message selector.
     * @return
     * @throws JMSException
     */
    private MessageConsumer createMessageConsumer(Destination replyToDestination, String messageId) throws JMSException {
        MessageConsumer messageConsumer;

        if (replyToDestination instanceof Queue) {
            messageConsumer = session.createConsumer(replyToDestination,
                    "JMSCorrelationID = '" + messageId.replaceAll("'", "''") + "'");
        } else {
            messageConsumer = session.createDurableSubscriber((Topic)replyToDestination, getName(),
                    "JMSCorrelationID = '" + messageId.replaceAll("'", "''") + "'", false);
        }

        return messageConsumer;
    }

    /**
     * Delete temporary destinations.
     * @param destination
     */
    private void deleteTemporaryDestination(Destination destination) {
        log.debug("Delete temporary destination: '{}'", destination);

        try {
            if (destination instanceof TemporaryQueue) {
                ((TemporaryQueue) destination).delete();
            } else if (destination instanceof TemporaryTopic) {
                ((TemporaryTopic) destination).delete();
            }
        } catch (JMSException e) {
            log.error("Error while deleting temporary destination '" + destination + "'", e);
        }
    }

    /**
     * Retrieve the reply destination either by injected instance, destination name or
     * by creating a new temporary destination.
     *
     * @param session current JMS session
     * @param message holding possible reply destination in header.
     * @return the reply destination.
     * @throws JMSException
     */
    private Destination getReplyDestination(Session session, Message message) throws JMSException {
        if (message.getHeader(org.springframework.messaging.MessageHeaders.REPLY_CHANNEL) != null) {
            if (message.getHeader(org.springframework.messaging.MessageHeaders.REPLY_CHANNEL) instanceof Destination) {
                return (Destination) message.getHeader(org.springframework.messaging.MessageHeaders.REPLY_CHANNEL);
            } else {
                return resolveDestinationName(message.getHeader(org.springframework.messaging.MessageHeaders.REPLY_CHANNEL).toString(), session);
            }
        } else if (endpointConfiguration.getReplyDestination() != null) {
            return endpointConfiguration.getReplyDestination();
        } else if (StringUtils.hasText(endpointConfiguration.getReplyDestinationName())) {
            return resolveDestinationName(endpointConfiguration.getReplyDestinationName(), session);
        }

        if (endpointConfiguration.isPubSubDomain() && session instanceof TopicSession){
            return session.createTemporaryTopic();
        } else {
            return session.createTemporaryQueue();
        }
    }

    /**
     * Get send destination either from injected destination instance or by resolving
     * a destination name.
     *
     * @param session current JMS session
     * @return the destination.
     * @throws JMSException
     */
    private Destination getDefaultDestination(Session session) throws JMSException {
        if (endpointConfiguration.getDestination() != null) {
            return endpointConfiguration.getDestination();
        }

        return resolveDestinationName(endpointConfiguration.getDestinationName(), session);
    }

    /**
     * Resolves the destination name from Jms session.
     * @param name
     * @param session
     * @return
     */
    private Destination resolveDestinationName(String name, Session session) throws JMSException {
        return new DynamicDestinationResolver().resolveDestinationName(session, name, endpointConfiguration.isPubSubDomain());
    }

    /**
     * Destroy method closing JMS session and connection
     */
    public void destroy() {
        JmsUtils.closeSession(session);

        if (connection != null) {
            ConnectionFactoryUtils.releaseConnection(connection, endpointConfiguration.getConnectionFactory(), true);
        }
    }

    /**
     * Gets the correlation manager.
     * @return
     */
    public CorrelationManager<Message> getCorrelationManager() {
        return correlationManager;
    }

    /**
     * Sets the correlation manager.
     * @param correlationManager
     */
    public void setCorrelationManager(CorrelationManager<Message> correlationManager) {
        this.correlationManager = correlationManager;
    }
}
