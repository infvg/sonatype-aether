package org.sonatype.aether.connector.async;

import io.netty.channel.ChannelProgressiveFuture;
import org.asynchttpclient.netty.NettyResponseFuture;
import org.asynchttpclient.netty.request.WriteProgressListener;
import org.sonatype.aether.transfer.TransferCancelledException;

import java.io.IOException;

public class ProgressingWriteProgressListener extends WriteProgressListener {
    CompletionHandler handler;
    public ProgressingWriteProgressListener(NettyResponseFuture<?> future, boolean notifyHeaders, long expectedTotal, CompletionHandler handler) {
        super(future, notifyHeaders, expectedTotal);
        this.handler = handler;
    }

    @Override
    public void operationComplete(ChannelProgressiveFuture cf) {
        try{
            this.handler.fireTransferSucceeded();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.operationComplete(cf);
    }
    @Override
    public void operationProgressed(ChannelProgressiveFuture f, long progress, long total) {
        try {
            this.handler.fireTransferProgressed(progress, total);
        } catch (TransferCancelledException e) {
            throw new RuntimeException(e);
        }
        super.operationProgressed(f, progress, total);

    }

}
