package org.sonatype.aether.connector.async;

import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.request.body.RandomAccessBody;
import org.asynchttpclient.request.body.generator.BodyGenerator;

import java.io.File;

import static org.asynchttpclient.util.Assertions.assertNotNull;

public class ProgressingFileBodyGenerator implements BodyGenerator {
    private final File file;
    private final long regionSeek;
    private final long regionLength;
    private final CompletionHandler handler;
    private final AsyncHttpClientConfig config;

    public ProgressingFileBodyGenerator(File file, AsyncHttpClientConfig config, CompletionHandler handler) {
        this(file, 0L, file.length(), config, handler);
    }

    public ProgressingFileBodyGenerator(File file, long regionSeek, long regionLength, AsyncHttpClientConfig config, CompletionHandler handler) {
        this.file = assertNotNull(file, "file");
        this.regionLength = regionLength;
        this.regionSeek = regionSeek;
        this.config = config;
        this.handler = handler;
    }

    public File getFile() {
        return file;
    }

    public long getRegionLength() {
        return regionLength;
    }

    public long getRegionSeek() {
        return regionSeek;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomAccessBody createBody() {
        return null;
        //return new ProgressingNettyFileBody(file,  regionSeek, regionLength, config, handler);
    }
}
