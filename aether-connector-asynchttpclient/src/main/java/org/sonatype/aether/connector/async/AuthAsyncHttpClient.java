package org.sonatype.aether.connector.async;

import org.asynchttpclient.*;

public class AuthAsyncHttpClient extends DefaultAsyncHttpClient {

    @Override
    public <T> ListenableFuture<T> executeRequest(Request request, AsyncHandler<T> handler) {
        return super.executeRequest(request, handler);
    }
}