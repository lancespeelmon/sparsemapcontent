/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.lite.content;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.CacheHolder;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.PrincipalValidatorResolver;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.ConfigurationImpl;
import org.sakaiproject.nakamura.lite.LoggingStorageListener;
import org.sakaiproject.nakamura.lite.accesscontrol.AccessControlManagerImpl;
import org.sakaiproject.nakamura.lite.accesscontrol.AuthenticatorImpl;
import org.sakaiproject.nakamura.lite.accesscontrol.PrincipalValidatorResolverImpl;
import org.sakaiproject.nakamura.lite.authorizable.AuthorizableActivator;
import org.sakaiproject.nakamura.lite.storage.ConcurrentLRUMap;
import org.sakaiproject.nakamura.lite.storage.StorageClient;
import org.sakaiproject.nakamura.lite.storage.StorageClientPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class AbstractContentManagerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContentManagerTest.class);
    private StorageClient client;
    private ConfigurationImpl configuration;
    private StorageClientPool clientPool;
    private Map<String, CacheHolder> sharedCache = new ConcurrentLRUMap<String, CacheHolder>(1000);
    private PrincipalValidatorResolver principalValidatorResolver = new PrincipalValidatorResolverImpl();

    @Before
    public void before() throws StorageClientException, AccessDeniedException, ClientPoolException,
            ClassNotFoundException {
        clientPool = getClientPool();
        client = clientPool.getClient();
        configuration = new ConfigurationImpl();
        Map<String, Object> properties = Maps.newHashMap();
        properties.put("keyspace", "n");
        properties.put("acl-column-family", "ac");
        properties.put("authorizable-column-family", "au");
        properties.put("content-column-family", "cn");
        configuration.activate(properties);
        AuthorizableActivator authorizableActivator = new AuthorizableActivator(client,
                configuration);
        authorizableActivator.setup();
        LOGGER.info("Setup Complete");
    }

    protected abstract StorageClientPool getClientPool() throws ClassNotFoundException;

    @After
    public void after() throws ClientPoolException {
        client.close();
    }

    @Test
    public void testCreateContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, null,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration, null,  new LoggingStorageListener());
        contentManager.update(new Content("/", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/");
        Assert.assertEquals("/", content.getPath());
        Map<String, Object> p = content.getProperties();
        LOGGER.info("Properties is {}",p);
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

    }
    
    @Test
    public void testSimpleDelete() throws AccessDeniedException, StorageClientException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        String path = "/testSimpleDelete/test2/test3/test4";
        String parentPath = "/testSimpleDelete/test2/test3";
        contentManager.update(new Content(parentPath, ImmutableMap.of("propParent", (Object) "valueParent")));
        contentManager.update(new Content(path, ImmutableMap.of("prop1", (Object) "value1")));
        Content content = contentManager.get(path);
        Assert.assertNotNull(content);
        Assert.assertEquals("value1", content.getProperty("prop1"));
        
        contentManager.delete(path);
        Assert.assertNull(contentManager.get(path));
        content = contentManager.get(parentPath);
        Assert.assertNotNull(content);
        Assert.assertEquals("valueParent", content.getProperty("propParent"));

        contentManager.delete("/testSimpleDelete");

    }

    @Test
    public void testSimpleDeleteRoot() throws AccessDeniedException, StorageClientException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        String path = "testSimpleDeleteRoot/test2/test3/test4";
        String parentPath = "testSimpleDeleteRoot/test2/test3";
        contentManager.update(new Content(parentPath, ImmutableMap.of("propParent", (Object) "valueParent")));
        contentManager.update(new Content(path, ImmutableMap.of("prop1", (Object) "value1")));
        Content content = contentManager.get(path);
        Assert.assertNotNull(content);
        Assert.assertEquals("value1", content.getProperty("prop1"));
        
        contentManager.delete(path);
        Assert.assertNull(contentManager.get(path));
        content = contentManager.get(parentPath);
        Assert.assertNotNull(content);
        Assert.assertEquals("valueParent", content.getProperty("propParent"));

        contentManager.delete("testSimpleDeleteRoot");

    }

    @Test
    public void testDeleteContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        contentManager.update(new Content("/", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/test/ing", ImmutableMap.of("prop1", (Object) "value3")));
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 5; k++) {
                    contentManager.update(new Content("/test/ing/" + i + "/" + j + "/" + k,
                            ImmutableMap.of("prop1", (Object) "value3")));
                }
            }
        }

        Content content = contentManager.get("/");
        Assert.assertEquals("/", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        StorageClientUtils.deleteTree(contentManager, "/test/ing");
        content = contentManager.get("/test/ing");
        Assert.assertNull(content);
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 5; k++) {
                    Assert.assertNull(contentManager.get("/test/ing/" + i + "/" + j + "/" + k));
                }
            }
        }

    }

    @Test
    public void testUpdateContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        contentManager.update(new Content("/", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/");
        Assert.assertEquals("/", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        p = content.getProperties();
        Assert.assertNull((String)p.get("prop1update"));

        content.setProperty("prop1update", "value4");
        contentManager.update(content);

        content = contentManager.get(content.getPath());
        p = content.getProperties();
        Assert.assertEquals("value4", (String)p.get("prop1update"));

    }

    @Test
    public void testVersionContent() throws StorageClientException, AccessDeniedException,
            InterruptedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        contentManager.update(new Content("/", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/");
        Assert.assertEquals("/", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        p = content.getProperties();
        Assert.assertNull((String)p.get("prop1update"));

        // FIXME: add some version list methods, we have no way of testing if
        // this works.
        String versionName = contentManager.saveVersion("/");

        // must reload after a version save.
        content = contentManager.get("/");

        content.setProperty("prop1update", "value4");
        contentManager.update(content);

        content = contentManager.get("/");
        p = content.getProperties();
        Assert.assertEquals("value4", (String)p.get("prop1update"));

        // just in case the machine is so fast all of that took 1ms
        Thread.sleep(50);

        String versionName2 = contentManager.saveVersion("/");

        Content versionContent = contentManager.getVersion("/", versionName);
        Assert.assertNotNull(versionContent);
        Content versionContent2 = contentManager.getVersion("/", versionName2);
        Assert.assertNotNull(versionContent2);
        List<String> versionList = contentManager.getVersionHistory("/");
        Assert.assertNotNull(versionList);
        Assert.assertArrayEquals("Version List is " + Arrays.toString(versionList.toArray())
                + " expecting " + versionName2 + " then " + versionName, new String[] {
                versionName2, versionName }, versionList.toArray(new String[versionList.size()]));

        Content badVersionContent = contentManager.getVersion("/", "BadVersion");
        Assert.assertNull(badVersionContent);

    }

    @Test
    public void testUploadContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        contentManager.update(new Content("/", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/");
        Assert.assertEquals("/", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        p = content.getProperties();
        Assert.assertNull((String)p.get("prop1update"));

        // FIXME: add some version list methods, we have no way of testing if
        // this works.
        contentManager.saveVersion("/");

        content = contentManager.get("/");

        content.setProperty("prop1update", "value4");
        contentManager.update(content);

        content = contentManager.get(content.getPath());
        p = content.getProperties();
        Assert.assertEquals("value4", (String)p.get("prop1update"));

        final byte[] b = new byte[20 * 1024 * 1024 + 1231];
        Random r = new Random();
        r.nextBytes(b);
        try {
            contentManager.update(new Content("/test/ing/testfile.txt", ImmutableMap.of(
                    "testproperty", (Object) "testvalue")));
            long su = System.currentTimeMillis();
            ByteArrayInputStream bais = new ByteArrayInputStream(b);
            contentManager.writeBody("/test/ing/testfile.txt", bais);
            bais.close();
            long eu = System.currentTimeMillis();

            InputStream read = contentManager.getInputStream("/test/ing/testfile.txt");

            int i = 0;
            byte[] buffer = new byte[8192];
            int j = read.read(buffer);
            Assert.assertNotSame(-1, j);
            while (j != -1) {
                // Assert.assertEquals((int)b[i] & 0xff, j);
                i = i + j;
                j = read.read(buffer);
            }
            read.close();
            Assert.assertEquals(b.length, i);
            long ee = System.currentTimeMillis();
            LOGGER.info("Write rate {} MB/s  Read Rate {} MB/s ",
                    (1000 * (double) b.length / (1024 * 1024 * (double) (eu - su))),
                    (1000 * (double) b.length / (1024 * 1024 * (double) (ee - eu))));

            // Update content and re-read
            r.nextBytes(b);
            bais = new ByteArrayInputStream(b);
            contentManager.writeBody("/test/ing/testfile.txt", bais);

            read = contentManager.getInputStream("/test/ing/testfile.txt");

            i = 0;
            j = read.read(buffer);
            Assert.assertNotSame(-1, j);
            while (j != -1) {
                for (int k = 0; k < j; k++) {
                    Assert.assertEquals(b[i], buffer[k]);
                    i++;
                }
                if ((i % 100 == 0) && (i < b.length - 20)) {
                    Assert.assertEquals(10, read.skip(10));
                    i += 10;
                }
                j = read.read(buffer);
            }
            read.close();
            Assert.assertEquals(b.length, i);

        } catch (IOException e) {

            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.fail();
        }

    }

  @Test
  public void testMoveWithChildren() throws StorageClientException, AccessDeniedException {
    AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
        currentUser, configuration, null, new LoggingStorageListener(), principalValidatorResolver);

    ContentManagerImpl contentManager = new ContentManagerImpl(client,
        accessControlManager, configuration, null, new LoggingStorageListener());
    contentManager.update(new Content("/", ImmutableMap.of("prop1", (Object) "value1")));
    contentManager.update(new Content("/movewc", ImmutableMap.of("prop1",
        (Object) "value2")));
    contentManager.update(new Content("/test", ImmutableMap
        .of("prop1", (Object) "value3")));
    contentManager.update(new Content("/test/ing", ImmutableMap.of("prop1",
        (Object) "value4")));
    contentManager.moveWithChildren("/test", "/movewc/test");

    Content content = contentManager.get("/");
    Assert.assertEquals("/", content.getPath());
    Map<String, Object> p = content.getProperties();
    LOGGER.info("Properties is {}", p);
    Assert.assertEquals("value1", (String) p.get("prop1"));
    Iterator<Content> children = content.listChildren().iterator();
    Assert.assertTrue(children.hasNext());
    Content child = children.next();
    Assert.assertFalse(children.hasNext());
    Assert.assertEquals("/movewc", child.getPath());
    p = child.getProperties();
    Assert.assertEquals("value2", (String) p.get("prop1"));
    children = child.listChildren().iterator();
    Assert.assertTrue(children.hasNext());
    child = children.next();
    Assert.assertFalse(children.hasNext());
    Assert.assertEquals("/movewc/test", child.getPath());
    p = child.getProperties();
    Assert.assertEquals("value3", (String) p.get("prop1"));
    children = child.listChildren().iterator();
    Assert.assertTrue(children.hasNext());
    child = children.next();
    Assert.assertFalse(children.hasNext());
    Assert.assertEquals("/movewc/test/ing", child.getPath());
    p = child.getProperties();
    Assert.assertEquals("value4", (String) p.get("prop1"));

  }

  /**
   * search for "a" find contentA
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindA() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.multiValueA[0]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" find contentA
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindA2() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.multiValueA[1]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "x" find contentX only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindX() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.multiValueX[0]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathX, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found only one match", found == 1);
  }

  /**
   * search for "x" find contentX only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindX2() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.multiValueX[1]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathX, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found only one match", found == 1);
  }

  /**
   * search for "x" find contentX only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindX3() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.multiValueX[2]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathX, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found only one match", found == 1);
  }

  /**
   * search for "a" or "b" find contentA only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAorB() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(MV.multiValueA));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" or "b" find contentA only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindBorA() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueA[1], MV.multiValueA[0] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "x" or "y" find contentX only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindXorY() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueX[0], MV.multiValueX[1] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathX, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found only one match", found == 1);
  }

  /**
   * search for "x" or "y" find contentX only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindXorZ() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueX[0], MV.multiValueX[2] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathX, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found only one match", found == 1);
  }

  /**
   * search for "a" and "b" find contentA only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAandB() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(MV.multiValueA));
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" and "x" find nothing
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAandX() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueA[0], MV.multiValueX[0] }));
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertFalse("Should NOT have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      foundA++;
    }
    Assert.assertTrue("Should NOT have found any match", foundA == 0);
  }

  /**
   * search for "a" and "x" find nothing
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAandX2() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueA[1], MV.multiValueX[1] }));
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertFalse("Should NOT have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      foundA++;
    }
    Assert.assertTrue("Should NOT have found any match", foundA == 0);
  }

  /**
   * search for "a" and "x" find nothing
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAandX3() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueA[1], MV.multiValueX[2] }));
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertFalse("Should NOT have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      foundA++;
    }
    Assert.assertTrue("Should NOT have found any match", foundA == 0);
  }

  /**
   * search for "a" or "x" find contentA and contentB
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAorX() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueA[0], MV.multiValueX[0] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertTrue("Path should match one of the two Contents",
          MV.pathA.equals(match.getPath()) || MV.pathX.equals(match.getPath()));
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("Multi-valued property should equal one of the two Contents",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey))
              || Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found two matches", found == 2);
  }

  /**
   * search for "a" or "x" find contentA and contentB
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAorX2() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueA[1], MV.multiValueX[1] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertTrue("Path should match one of the two Contents",
          MV.pathA.equals(match.getPath()) || MV.pathX.equals(match.getPath()));
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("Multi-valued property should equal one of the two Contents",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey))
              || Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found two matches", found == 2);
  }

  /**
   * search for "a" or "x" find contentA and contentB
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAorX3() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupMultiValuedIndexSearch();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays.asList(new String[] { MV.multiValueA[1], MV.multiValueX[2] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int found = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertTrue("Path should match one of the two Contents",
          MV.pathA.equals(match.getPath()) || MV.pathX.equals(match.getPath()));
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("Multi-valued property should equal one of the two Contents",
          Arrays.equals(MV.multiValueA, (String[]) match.getProperty(MV.propKey))
              || Arrays.equals(MV.multiValueX, (String[]) match.getProperty(MV.propKey)));
      found++;
    }
    Assert.assertTrue("Should have found two matches", found == 2);
  }

  /**
   * search for "a" find contentA
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAltA() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupAlternateMultiValuedProperties();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.altMultiValueA[0]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.altMultiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" find contentA
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAltA2() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupAlternateMultiValuedProperties();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.altMultiValueA[1]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.altMultiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" find contentA
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAltA3() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupAlternateMultiValuedProperties();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) MV.altMultiValueA[2]);
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.altMultiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" or "b" find contentA only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAltAorB() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupAlternateMultiValuedProperties();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays
            .asList(new String[] { MV.altMultiValueA[0], MV.altMultiValueA[1] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.altMultiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" or "b" find contentA only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAltAorB2() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupAlternateMultiValuedProperties();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays
            .asList(new String[] { MV.altMultiValueA[1], MV.altMultiValueA[2] }));
    final Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    final Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.altMultiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * search for "a" and "b" find contentA only once
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testMultiValuedIndexSearchFindAltAandB() throws StorageClientException,
      AccessDeniedException {
    final ContentManager contentManager = setupAlternateMultiValuedProperties();
    final Map<String, Object> searchCriteria = ImmutableMap.of(MV.propKey,
        (Object) Arrays
            .asList(new String[] { MV.altMultiValueA[0], MV.altMultiValueA[1] }));
    final Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull("Iterable should not be null", iterable);
    final Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    int foundA = 0;
    while (iter.hasNext()) {
      final Content match = iter.next();
      Assert.assertNotNull("match should not be null", match);
      Assert.assertEquals(MV.pathA, match.getPath());
      Assert.assertNotNull("match should have key: " + MV.propKey,
          match.getProperty(MV.propKey));
      Assert.assertTrue("String[] should be equal",
          Arrays.equals(MV.altMultiValueA, (String[]) match.getProperty(MV.propKey)));
      foundA++;
    }
    Assert.assertTrue("Should have found only one match", foundA == 1);
  }

  /**
   * Create two contents with default values
   * 
   * @return
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private ContentManager setupMultiValuedIndexSearch() throws StorageClientException,
      AccessDeniedException {
    AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
        currentUser, configuration, null, new LoggingStorageListener(),
        principalValidatorResolver);
    ContentManager contentManager = new ContentManagerImpl(client, accessControlManager,
        configuration, null, new LoggingStorageListener());
    // add some content with multi-valued properties
    Content contentA = contentManager.get(MV.pathA);
    if (contentA == null) {
      contentManager.update(new Content(MV.pathA, ImmutableMap.of(MV.propKey,
          (Object) MV.multiValueA)));
    } else {
      contentA.setProperty(MV.propKey, (Object) MV.multiValueA);
      contentManager.update(contentA);
    }
    Content contentX = contentManager.get(MV.pathX);
    if (contentX == null) {
      contentManager.update(new Content(MV.pathX, ImmutableMap.of(MV.propKey,
          (Object) MV.multiValueX)));
    } else {
      contentX.setProperty(MV.propKey, (Object) MV.multiValueX);
      contentManager.update(contentX);
    }

    // verify state of content
    contentA = contentManager.get(MV.pathA);
    contentX = contentManager.get(MV.pathX);
    Assert.assertEquals(MV.pathA, contentA.getPath());
    Assert.assertEquals(MV.pathX, contentX.getPath());
    Map<String, Object> propsA = contentA.getProperties();
    Map<String, Object> propsX = contentX.getProperties();
    Assert.assertTrue(Arrays.equals(MV.multiValueA, (String[]) propsA.get(MV.propKey)));
    Assert.assertTrue(Arrays.equals(MV.multiValueX, (String[]) propsX.get(MV.propKey)));
    return contentManager;
  }

  /**
   * Change the values of the properties to something else
   * 
   * @return
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private ContentManager setupAlternateMultiValuedProperties()
      throws StorageClientException, AccessDeniedException {
    ContentManager contentManager = setupMultiValuedIndexSearch();
    // set some alternate multi-valued properties
    Content contentA = contentManager.get(MV.pathA);
    contentA.setProperty(MV.propKey, (Object) MV.altMultiValueA);
    contentManager.update(contentA);
    Content contentX = contentManager.get(MV.pathX);
    contentX.setProperty(MV.propKey, (Object) MV.altMultiValueX);
    contentManager.update(contentX);

    // verify state of content
    contentA = contentManager.get(MV.pathA);
    contentX = contentManager.get(MV.pathX);
    Assert.assertEquals(MV.pathA, contentA.getPath());
    Assert.assertEquals(MV.pathX, contentX.getPath());
    Map<String, Object> propsA = contentA.getProperties();
    Map<String, Object> propsX = contentX.getProperties();
    Assert
        .assertTrue(Arrays.equals(MV.altMultiValueA, (String[]) propsA.get(MV.propKey)));
    Assert
        .assertTrue(Arrays.equals(MV.altMultiValueX, (String[]) propsX.get(MV.propKey)));
    return contentManager;
  }

  private static class MV {
    private static final String propKey = "sakai:tag-uuid";
    private static final String pathA = "/multi/pathA";
    private static final String pathX = "/multi/pathX";
    private static final String[] multiValueA = new String[] { "valueA", "valueB" };
    private static final String[] multiValueX = new String[] { "valueX", "valueY",
        "valueZ" };
    private static final String[] altMultiValueA = multiValueX;
    private static final String[] altMultiValueX = multiValueA;
  }
}
