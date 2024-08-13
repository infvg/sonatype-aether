package org.sonatype.aether.connector.async;

import io.netty.channel.Channel;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedNioFile;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.netty.NettyResponseFuture;
import org.asynchttpclient.netty.channel.ChannelManager;
import org.asynchttpclient.netty.request.WriteProgressListener;
import org.asynchttpclient.netty.request.body.NettyFileBody;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class ProgressingNettyFileBody extends NettyFileBody {
    private final File file;
    private final long offset;
    private final long length;
    private final AsyncHttpClientConfig config;

    public ProgressingNettyFileBody(File file, AsyncHttpClientConfig config) {
        this(file, 0, file.length(), config);
    }

    public ProgressingNettyFileBody(File file, long offset, long length, AsyncHttpClientConfig config) {
        super(file, offset, length, config);
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("File %s is not a file or doesn't exist", file.getAbsolutePath()));
        }
        this.file = file;
        this.offset = offset;
        this.length = length;
        this.config = config;
    }


    @Override
    public void write(Channel channel, NettyResponseFuture<?> future) throws IOException {
        FileChannel fileChannel = new RandomAccessFile(file, "r").getChannel();
        boolean noZeroCopy = ChannelManager.isSslHandlerConfigured(channel.pipeline()) || config.isDisableZeroCopy();
        Object body = noZeroCopy ? new ChunkedNioFile(fileChannel, offset, length, config.getChunkedFileChunkSize()) : new DefaultFileRegion(fileChannel, offset, length);

        channel.write(body, channel.newProgressivePromise())
                .addListener(new WriteProgressListener(future, false, length));
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, channel.voidPromise());
    }

}
