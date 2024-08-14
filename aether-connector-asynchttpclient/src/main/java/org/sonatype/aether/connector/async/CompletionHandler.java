package org.sonatype.aether.connector.async;

/*******************************************************************************
 * Copyright (c) 2010-2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/


import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.*;
import org.sonatype.aether.spi.log.Logger;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferListener;
import org.sonatype.aether.transfer.TransferResource;
import org.sonatype.aether.util.listener.DefaultTransferResource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link AsyncCompletionHandler} for handling asynchronous download an upload.
 *
 * @author Jeanfrancois Arcand
 */
class CompletionHandler extends AsyncCompletionHandler<Response> {
    private final Logger logger;

    private final ConcurrentLinkedQueue<TransferListener> listeners = new ConcurrentLinkedQueue<TransferListener>();


    private final AtomicLong byteTransfered = new AtomicLong();

    private HttpResponseStatus status;


    private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

    private final DefaultTransferResource transferResource;

    private final TransferEvent.RequestType requestType;
    private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

    public CompletionHandler( DefaultTransferResource transferResource, Logger logger,
                              TransferEvent.RequestType requestType )
    {
        this.transferResource = transferResource;
        this.logger = logger;
        this.requestType = requestType;
    }

    @Override
    public State onHeadersWritten()
    {
        if ( TransferEvent.RequestType.PUT.equals( requestType ) )
        {
            byteTransfered.set( 0 );
            try
            {
                fireTransferStarted();
            }
            catch ( TransferCancelledException e )
            {
                return State.ABORT;
            }
        }
        return State.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State onBodyPartReceived( final HttpResponseBodyPart content )
        throws Exception
    {
        try
        {
            fireTransferProgressed( content.getBodyPartBytes() );
            this.builder.accumulate(content);
        }
        catch ( TransferCancelledException e )
        {
            return State.ABORT;
        }
        catch ( Exception ex )
        {
            if ( logger.isDebugEnabled() )
            {
                logger.debug( "", ex );
            }
        }
        return State.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State onStatusReceived( final HttpResponseStatus status )
        throws Exception
    {
        this.builder.reset();
        this.builder.accumulate(status);
        this.status = status;
        return ( status.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND ? State.ABORT : State.CONTINUE );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public State onHeadersReceived( final HttpHeaders headers )
        throws Exception
    {
        this.builder.accumulate(headers);

        if ( !TransferEvent.RequestType.PUT.equals( requestType ) )
        {
            if ( status.getStatusCode() >= 200 && status.getStatusCode() < 300 )
            {
                try
                {
                    transferResource.setContentLength( Long.parseLong( headers.get( "Content-Length" ) ) );
                }
                catch ( RuntimeException e )
                {
                    // oh well, no parsable content length
                }
                try
                {
                    fireTransferStarted();
                }
                catch ( TransferCancelledException e )
                {
                    return State.ABORT;
                }
            }
        }

        return State.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onThrowable( Throwable t )
    {
        exception.set( t );
    }
    /**
     * Invoked once the HTTP response has been fully read.
     *
     * @param response The {@link Response}
     * @return Type of the value that will be returned by the associated {@link java.util.concurrent.Future}
     */

    public Response onCompleted(Response response) throws Exception {
        if (response != null && response.hasResponseStatus() && response.getStatusCode() >= HttpURLConnection.HTTP_OK
            && response.getStatusCode() <= HttpURLConnection.HTTP_CREATED)
            fireTransferSucceeded(response);
        return this.builder.build();
    }

    void fireTransferProgressed( final byte[] buffer )
        throws TransferCancelledException
    {
        fireTransferProgressed( ByteBuffer.wrap( buffer ) );
    }



    void fireTransferProgressed( final ByteBuffer buffer )
        throws TransferCancelledException
    {
        final long bytesTransferred = byteTransfered.addAndGet( buffer.remaining() );

        final TransferEvent transferEvent = new AsyncTransferEvent()
        {

            public EventType getType()
            {
                return TransferEvent.EventType.PROGRESSED;
            }

            public long getTransferredBytes()
            {
                return bytesTransferred;
            }

            public ByteBuffer getDataBuffer()
            {
                return buffer.asReadOnlyBuffer();
            }

            public int getDataLength()
            {
                return buffer.remaining();
            }

        };

        for ( Iterator<TransferListener> iter = listeners.iterator(); iter.hasNext(); )
        {
            final TransferListener listener = iter.next();
            listener.transferProgressed( transferEvent );
        }
    }

    void fireTransferSucceeded( final Response response )
        throws IOException
    {
        response.getResponseBodyAsBytes();
        final long bytesTransferred = byteTransfered.get();

        final TransferEvent transferEvent = new AsyncTransferEvent()
        {

            public EventType getType()
            {
                return TransferEvent.EventType.SUCCEEDED;
            }

            public long getTransferredBytes()
            {
                return bytesTransferred;
            }

        };

        for ( Iterator<TransferListener> iter = listeners.iterator(); iter.hasNext(); )
        {
            final TransferListener listener = iter.next();
            listener.transferSucceeded( transferEvent );
        }
    }
    void fireTransferSucceeded()
            throws IOException
    {
        final long bytesTransferred = byteTransfered.get();

        final TransferEvent transferEvent = new AsyncTransferEvent()
        {

            public EventType getType()
            {
                return TransferEvent.EventType.SUCCEEDED;
            }

            public long getTransferredBytes()
            {
                return bytesTransferred;
            }

        };

        for ( Iterator<TransferListener> iter = listeners.iterator(); iter.hasNext(); )
        {
            final TransferListener listener = iter.next();
            listener.transferSucceeded( transferEvent );
        }
    }

    void fireTransferFailed()
        throws IOException
    {
        final long bytesTransferred = byteTransfered.get();

        final TransferEvent transferEvent = new AsyncTransferEvent()
        {

            public EventType getType()
            {
                return TransferEvent.EventType.FAILED;
            }

            public long getTransferredBytes()
            {
                return bytesTransferred;
            }

        };

        for ( Iterator<TransferListener> iter = listeners.iterator(); iter.hasNext(); )
        {
            final TransferListener listener = iter.next();
            listener.transferFailed( transferEvent );

        }
    }

    void fireTransferStarted()
        throws TransferCancelledException
    {
        final TransferEvent transferEvent = new AsyncTransferEvent()
        {

            public EventType getType()
            {
                return TransferEvent.EventType.STARTED;
            }

            public long getTransferredBytes()
            {
                return 0;
            }

        };

        for ( Iterator<TransferListener> iter = listeners.iterator(); iter.hasNext(); )
        {
            final TransferListener listener = iter.next();
            listener.transferStarted( transferEvent );
        }
    }

    public boolean addTransferListener( TransferListener listener )
    {
        if ( listener == null )
        {
            return false;
        }
        return listeners.offer( listener );
    }

    public boolean removeTransferListener( TransferListener listener )
    {
        if ( listener == null )
        {
            return false;
        }
        return listeners.remove( listener );
    }

    protected HttpResponseStatus status()
    {
        return status;
    }

    abstract class AsyncTransferEvent
        implements TransferEvent
    {

        public RequestType getRequestType()
        {
            return requestType;
        }

        public TransferResource getResource()
        {
            return transferResource;
        }

        public ByteBuffer getDataBuffer()
        {
            return null;
        }

        public int getDataLength()
        {
            return 0;
        }

        public Exception getException()
        {
            return ( Exception.class.isAssignableFrom( exception.get().getClass() ) ? Exception.class.cast( exception.get() )
                            : new Exception( exception.get() ) );
        }

        @Override
        public String toString()
        {
            return getRequestType() + " " + getType() + " " + getResource();
        }

    }

}
