/*
 * Copyright 2006-2010 the original author or authors.
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

package com.consol.citrus.message;

import org.springframework.integration.core.Message;

/**
 * Message sender interface.
 * 
 * @author Christoph Deppisch
 */
public interface MessageSender {
    /**
     * Sends the message to the default endpoint.
     * @param message the message object to send.
     */
    void send(Message<?> message);
    
    /**
     * Sends a message to a dynamic endpoint.
     * @param message the message object to send.
     * @param endpoint the String representation (name, uri, queue name) of the target endpoint
     */
    void send(Message<?> message, String endpoint);
}
