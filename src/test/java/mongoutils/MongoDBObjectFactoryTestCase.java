package mongoutils;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.naming.RefAddr;
import javax.naming.Reference;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foursquare.fongo.Fongo;
import com.mongodb.DB;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

import mongoutils.MongoDBObjectFactory;

public class MongoDBObjectFactoryTestCase {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    private class FongoDBObjectFactory extends MongoDBObjectFactory {

        public List<MongoCredential> credentials;

        @Override
        protected DB createDB(final String address, final String database, final List<MongoCredential> credentials)
                throws UnknownHostException {
            Fongo fongo = new Fongo(address);
            this.credentials = credentials;
            return fongo.getDB(database);
        }

        @Override
        protected DB createDB(final List<ServerAddress> seeds, final String database,
                final List<MongoCredential> credentials) throws UnknownHostException {
            // Fongo doesn't support seed lists just concat the strings
            Fongo fongo = new Fongo(StringUtils.join(seeds, "|"));
            this.credentials = credentials;
            return fongo.getDB(database);
        }
    }

    @Test
    public void testNullInstantiation() throws Exception {

        FongoDBObjectFactory factory = new FongoDBObjectFactory();
        Object obj = factory.getObjectInstance(null, null, null, null);
        assertNull(obj);

        obj = factory.getObjectInstance("not a reference", null, null, null);
        assertNull(obj);
    }
    @Test

    public void testIllegalArg() throws Exception {

        FongoDBObjectFactory factory = new FongoDBObjectFactory();

        RefAddr writeConcern = mock(RefAddr.class);
        when(writeConcern.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_WRITE_CONCERN);
        when(writeConcern.getContent()).thenReturn("Unknown write concern");

        List<RefAddr> properties = new LinkedList<>();
        properties.add(writeConcern);

        Reference reference = mock(Reference.class);
        when(reference.getAll()).thenReturn(Collections.enumeration(properties));
        
        try {
            factory.getObjectInstance(reference, null, null, null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("concern"));
        }
        
        RefAddr readPreference = mock(RefAddr.class);
        when(readPreference.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_READ_PREFERENCE);
        when(readPreference.getContent()).thenReturn("Unknown Read Preference");
        properties.clear();
        properties.add(readPreference);

        when(reference.getAll()).thenReturn(Collections.enumeration(properties));
        try {
            factory.getObjectInstance(reference, null, null, null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("preference"));
        }
    }

    @Test
    public void testValidInstantiation() throws Exception {

        FongoDBObjectFactory factory = new FongoDBObjectFactory();

        RefAddr address = mock(RefAddr.class);
        when(address.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_ADDRESS);
        when(address.getContent()).thenReturn("127.0.0.1");

        RefAddr database = mock(RefAddr.class);
        when(database.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_DATABASE);
        when(database.getContent()).thenReturn("some_db");

        RefAddr userName = mock(RefAddr.class);
        when(userName.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_USERNAME);
        when(userName.getContent()).thenReturn("some_user");

        RefAddr password = mock(RefAddr.class);
        when(password.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_PASSWORD);
        when(password.getContent()).thenReturn("some_password");

        RefAddr writeConcern = mock(RefAddr.class);
        when(writeConcern.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_WRITE_CONCERN);
        when(writeConcern.getContent()).thenReturn("MAJORITY");

        RefAddr readPreference = mock(RefAddr.class);
        when(readPreference.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_READ_PREFERENCE);
        when(readPreference.getContent()).thenReturn("NEAREST");

        List<RefAddr> properties = new LinkedList<>();
        properties.add(address);
        properties.add(database);
        properties.add(userName);
        properties.add(password);
        properties.add(writeConcern);
        properties.add(readPreference);

        Reference reference = mock(Reference.class);
        when(reference.getAll()).thenReturn(Collections.enumeration(properties));

        DB db = (DB) factory.getObjectInstance(reference, null, null, null);
        assertNotNull(db);
        assertEquals("some_db", db.getName());

        assertEquals(Collections.singletonList(MongoCredential.createMongoCRCredential("some_user", "some_db",
                "some_password".toCharArray())), factory.credentials);
        // FongoDB doesn't report the write concern / read preference correctly, ask the factory directly.
        assertEquals(WriteConcern.MAJORITY, factory.getWriteConcern());
        assertEquals(ReadPreference.nearest(), factory.getReadPreference());
    }

    @Test
    public void testSeedList() throws Exception {
        MongoDBObjectFactory factory = new FongoDBObjectFactory();

        RefAddr seed = mock(RefAddr.class);
        when(seed.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_SEEDS);

        // Comma's or spaces are allowed as address separator.
        when(seed.getContent()).thenReturn("127.0.0.1, 127.0.0.2,127.0.0.3");

        RefAddr database = mock(RefAddr.class);
        when(database.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_DATABASE);
        when(database.getContent()).thenReturn("some_db");

        List<RefAddr> properties = new LinkedList<>();
        properties.add(seed);
        properties.add(database);
        Reference reference = mock(Reference.class);
        when(reference.getAll()).thenReturn(Collections.enumeration(properties));

        DB db = (DB) factory.getObjectInstance(reference, null, null, null);
        assertNotNull(db);
        assertEquals("some_db", db.getName());
        assertEquals(Arrays.asList(new ServerAddress("127.0.0.1"), new ServerAddress("127.0.0.2"),
                new ServerAddress("127.0.0.3")), factory.getSeeds());
    }

    @Test
    public void testInvalidProperties() throws Exception {

        MongoDBObjectFactory factory = new FongoDBObjectFactory();

        RefAddr address = mock(RefAddr.class);
        when(address.getType()).thenReturn(MongoDBObjectFactory.PROPERTY_ADDRESS);
        when(address.getContent()).thenReturn("127.0.0.1");

        List<RefAddr> properties = new LinkedList<>();
        properties.add(address);

        Reference reference = mock(Reference.class);
        when(reference.getAll()).thenReturn(Collections.enumeration(properties));

        DB db = (DB) factory.getObjectInstance(reference, null, null, null);
        assertNull(db);
    }
    
    @Test
    public void testIsBlank() {
        MongoDBObjectFactory f = new MongoDBObjectFactory();
        assertTrue(f.isBlank(null));
        assertTrue(f.isBlank(""));
        assertTrue(f.isBlank("  "));
    }
}
