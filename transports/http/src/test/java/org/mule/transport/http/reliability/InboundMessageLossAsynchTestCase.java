/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.http.reliability;

import org.mule.transport.http.HttpConstants;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.PostMethod;

public class InboundMessageLossAsynchTestCase extends InboundMessageLossTestCase
{
    @Override
    protected String getConfigResources()
    {
        return "reliability/inbound-message-loss-asynch.xml";
    }

    public void testNoException() throws Exception
    {
        HttpMethodBase request = createRequest(getBaseUri() + "/noException");
        int status = httpClient.executeMethod(request);
        assertEquals(HttpConstants.SC_OK, status);
    }
    
    public void testComponentException() throws Exception
    {
        HttpMethodBase request = createRequest(getBaseUri() + "/componentException");
        int status = httpClient.executeMethod(request);
        // Component exception occurs after the SEDA queue for an asynchronous request, so from the client's
        // perspective, the message has been delivered successfully.
        assertEquals(HttpConstants.SC_OK, status);
    }    

    @Override
    protected HttpMethodBase createRequest(String uri)
    {
        return new PostMethod(uri);
    }    
}
