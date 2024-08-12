

import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.RequestBuilderBase;

import java.net.MalformedURLException;
import java.net.URL;

public class Test {
    @org.junit.Test
    public void temp() throws MalformedURLException {
        RequestBuilder builder = new RequestBuilder();
        builder.setUrl("http://1.2.3.4:81#@5.6.7.8:82/aaa/b?q");
        Request request = builder.build();
        System.out.println(request.getUrl());
        // http://1.2.3.4:81
        System.out.println(new URL("http://1.2.3.4:81#@5.6.7.8:82/aaa/b?q").getHost());
        // 1.2.3.4

    }
}
