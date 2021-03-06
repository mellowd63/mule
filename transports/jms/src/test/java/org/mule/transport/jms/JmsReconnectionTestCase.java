/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.jms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.mule.api.MuleMessage;
import org.mule.api.transport.MessageReceiver;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;

import java.util.Collection;

import javax.jms.Connection;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.Rule;
import org.junit.Test;

public class JmsReconnectionTestCase extends FunctionalTestCase
{

    private static final int CONSUMER_COUNT = 4;
    private static final int TIMEOUT_MILLIS = 5000;
    private static final String PAYLOAD = "HELLO";

    @Rule
    public DynamicPort port = new DynamicPort("port");

    private BrokerService broker;
    private String url;
    private MultiConsumerJmsMessageReceiver receiver;
    private Connection connection;

    @Override
    protected String getConfigFile()
    {
        return "jms-reconnection-config.xml";
    }

    @Override
    protected void doSetUpBeforeMuleContextCreation() throws Exception
    {
        this.url = "tcp://localhost:" + this.port.getValue();
        this.startBroker();
    }

    @Override
    protected void doTearDownAfterMuleContextDispose() throws Exception
    {
        this.stopBroker();
    }

    private void startBroker() throws Exception
    {
        this.broker = new BrokerService();
        this.broker.setUseJmx(false);
        this.broker.setPersistent(false);
        this.broker.addConnector(this.url);
        this.broker.start(true);
        this.broker.waitUntilStarted();

        // this is needed because for some reason the broker will reject any connections
        // otherwise
        this.connection = new ActiveMQConnectionFactory(this.url).createQueueConnection();
    }

    private void stopBroker() throws Exception
    {
        this.connection.close();
        this.broker.stop();
        this.broker.waitUntilStopped();
    }

    @Test
    public void reconnectAllConsumers() throws Exception
    {
        this.assertMessageRouted();

        final JmsConnector connector = muleContext.getRegistry().lookupObject("activemqconnector");

        Collection<MessageReceiver> receivers = connector.getReceivers().values();
        assertTrue(receivers != null && receivers.size() == 1);
        this.receiver = (MultiConsumerJmsMessageReceiver) receivers.iterator().next();
        assertConsumersCount();

        this.stopBroker();

        PollingProber prober = new PollingProber(TIMEOUT_MILLIS, 500);
        prober.check(new Probe()
        {
            @Override
            public boolean isSatisfied()
            {
                return receiver.consumers.isEmpty();
            }

            @Override
            public String describeFailure()
            {
                return "consumers were never released";
            }
        });

        this.startBroker();

        prober.check(new Probe()
        {
            @Override
            public boolean isSatisfied()
            {
                try
                {
                    assertConsumersCount();
                    return true;
                }
                catch (AssertionError e)
                {
                    return false;
                }
            }

            @Override
            public String describeFailure()
            {
                return "receivers never came back";
            }
        });

        this.assertMessageRouted();
    }

    private void assertConsumersCount()
    {
        assertEquals(CONSUMER_COUNT, this.receiver.consumers.size());

        for (MultiConsumerJmsMessageReceiver.SubReceiver consumer : this.receiver.consumers)
        {
            assertTrue(consumer.connected);
        }
    }

    private void assertMessageRouted() throws Exception
    {
        this.runFlow("put", PAYLOAD);
        MuleMessage message = muleContext.getClient().request("vm://out", TIMEOUT_MILLIS);
        assertNotNull(message);
        assertEquals(PAYLOAD, message.getPayload());
    }
}
