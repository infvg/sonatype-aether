package org.sonatype.aether.connector.async;

import io.netty.channel.ChannelProgressiveFuture;
import org.asynchttpclient.netty.NettyResponseFuture;
import org.asynchttpclient.netty.request.WriteProgressListener;

public class ProgressingWriteProgressListener extends WriteProgressListener {
    CompletionHandler handler;
    public ProgressingWriteProgressListener(NettyResponseFuture<?> future, boolean notifyHeaders, long expectedTotal, CompletionHandler handler) {
        super(future, notifyHeaders, expectedTotal);
        this.handler = handler;
    }

    @Override
    public void operationProgressed(ChannelProgressiveFuture f, long progress, long total) {
        this.handler.fireTransferProgressed(progress);
        super.operationProgressed(f, progress, total);

    }

}
