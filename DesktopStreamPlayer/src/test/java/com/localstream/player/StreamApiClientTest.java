package com.localstream.player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.localstream.player.api.StreamApiClient;
import com.localstream.player.model.RemoteFile;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class StreamApiClientTest {

    @Test
    public void testUrlNormalization() {
        StreamApiClient client = new StreamApiClient();
        Assert.assertEquals("http://192.168.43.1:8080", client.normalizeBaseUrl("192.168.43.1:8080"));
        Assert.assertEquals("http://192.168.43.1:8080", client.normalizeBaseUrl("http://192.168.43.1:8080/"));
        Assert.assertEquals("https://10.0.0.5:9000", client.normalizeBaseUrl("https://10.0.0.5:9000///"));
    }

    @Test
    public void testBuildStreamUrl() {
        StreamApiClient client = new StreamApiClient();
        String url = client.buildStreamUrl("http://192.168.43.1:8080", "42", "7890");
        Assert.assertEquals("http://192.168.43.1:8080/stream/42?pin=7890", url);
    }

    @Test
    public void testRemoteFileJsonParsing() {
        String mockJsonResponse = "[\n" +
                "  {\"id\": \"1\", \"name\": \"sample.mp4\", \"mimeType\": \"video/mp4\", \"size\": 10485760},\n" +
                "  {\"id\": \"2\", \"name\": \"nature.webm\", \"mimeType\": \"video/webm\", \"size\": 5242880}\n" +
                "]";

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<RemoteFile>>() {}.getType();
        List<RemoteFile> files = gson.fromJson(mockJsonResponse, listType);

        Assert.assertNotNull(files);
        Assert.assertEquals(2, files.size());

        RemoteFile f1 = files.get(0);
        Assert.assertEquals("1", f1.getId());
        Assert.assertEquals("sample.mp4", f1.getName());
        Assert.assertEquals("video/mp4", f1.getMimeType());
        Assert.assertEquals(10485760L, f1.getSize());
        Assert.assertTrue(f1.isVideo());
        Assert.assertTrue(f1.isMp4());
        Assert.assertFalse(f1.isWebM());
        Assert.assertEquals("10 MB", f1.getFormattedSize());

        RemoteFile f2 = files.get(1);
        Assert.assertEquals("2", f2.getId());
        Assert.assertEquals("nature.webm", f2.getName());
        Assert.assertEquals("video/webm", f2.getMimeType());
        Assert.assertTrue(f2.isVideo());
        Assert.assertTrue(f2.isWebM());
        Assert.assertEquals("5 MB", f2.getFormattedSize());
    }
}
