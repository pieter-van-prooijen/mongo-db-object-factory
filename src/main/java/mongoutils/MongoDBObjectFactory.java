package mongoutils;

import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

/**
 * A JNDI object factory for Mongo DB objects, for use as a configurable datasource in Tomcat, JBoss etc.
 * 
 * @author pieter
 * 
 */
public class MongoDBObjectFactory implements ObjectFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBObjectFactory.class);

    // host<:port>
    public static final String PROPERTY_ADDRESS = "address";

    // host<:port>,host1<:port1>,...
    public static final String PROPERTY_SEEDS = "seeds";

    // db-name
    public static final String PROPERTY_DATABASE = "database";

    public static final String PROPERTY_USERNAME = "username";

    public static final String PROPERTY_PASSWORD = "password";

    // write-concerns: use one of the constants defined in the WriteConcern class.
    public static final String PROPERTY_WRITE_CONCERN = "writeConcern";

    // read preference: use one of the constants defined in ReadPreference class.
    public static final String PROPERTY_READ_PREFERENCE = "readPreference";
    private static final Pattern PAT_BLANK = Pattern.compile("^\\s*$");

    private String address = null;
    private List<ServerAddress> seeds = new LinkedList<>();
    private String database = null;
    private String userName = null;
    private char[] password = new char[0];
    private WriteConcern writeConcern = null;
    private ReadPreference readPreference = null;

    @Override
    @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "PMD.ReplaceHashtableWithMap" })
    public Object getObjectInstance(final Object obj, final Name name, final Context nameCtx,
            final Hashtable<?, ?> environment) throws Exception {

        if (!(obj instanceof Reference)) {
            return null;
        }

        Reference ref = (Reference) obj;
        Enumeration<RefAddr> refAddrs = ref.getAll();
        while (refAddrs.hasMoreElements()) {
            RefAddr refAddr = refAddrs.nextElement();

            String property = refAddr.getType();
            String value = (String) refAddr.getContent();

            handleProperty(property, value);
        }
        return getDB();
    }

    private void handleProperty(final String property, final String value) throws UnknownHostException {
        switch (property) {

        case PROPERTY_ADDRESS:
            address = value;
            break;
        case PROPERTY_SEEDS:
            setSeeds(value);
            break;
        case PROPERTY_USERNAME:
            userName = value;
            break;
        case PROPERTY_PASSWORD:
            password = value.toCharArray();
            break;
        case PROPERTY_DATABASE:
            database = value;
            break;
        case PROPERTY_WRITE_CONCERN:
            writeConcern = WriteConcern.valueOf(value);
            if (writeConcern == null) {
                throw new IllegalArgumentException("Illegal value of write concern " + value);
            }
            break;
        case PROPERTY_READ_PREFERENCE:
            readPreference = ReadPreference.valueOf(value);
            break;
        default:
            // Unknown property, not handled.
            break;
        }
    }

    private boolean validate() {
        if (isBlank(address) && seeds.isEmpty()) {
            LOG.error("Either an address or a seeds property is required");
            return false;
        }
        if (isBlank(database)) {
            LOG.error("A database property is required");
            return false;
        }
        return true;
    }

    private DB getDB() throws UnknownHostException {
        if (validate()) {
            DB db;

            List<MongoCredential> credentials = new LinkedList<>();
            if (isNotBlank(userName)) {
                MongoCredential credential = MongoCredential.createMongoCRCredential(userName, database, password);
                credentials.add(credential);
            }
            if (isNotBlank(address)) {
                db = createDB(address, database, credentials);
            } else {
                db = createDB(seeds, database, credentials);
            }
            if (writeConcern != null) {
                db.setWriteConcern(writeConcern);
            }
            if (readPreference != null) {
                db.setReadPreference(readPreference);
            }
            return db;
        }
        return null;
    }

    // Mongo DB creation broken out for unit testing.
    protected DB createDB(final String address, final String database, final List<MongoCredential> credentials)
            throws UnknownHostException {
        Mongo mongo = new MongoClient(new ServerAddress(address), credentials);
        return mongo.getDB(database);
    }

    protected DB createDB(final List<ServerAddress> seeds, final String database,
            final List<MongoCredential> credentials) throws UnknownHostException {
        Mongo mongo = new MongoClient(seeds, credentials);
        return mongo.getDB(database);
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

    public void setSeeds(final String seedsArg) throws UnknownHostException {
        if (isNotBlank(seedsArg)) {
            String[] seedValues = seedsArg.split("[,\\s]+");
        for (String seedValue : seedValues) {
            seeds.add(new ServerAddress(seedValue));
            }
        }
    }

    public List<ServerAddress> getSeeds() {
        return seeds;
    }
    
    // Remove dependency on commons-lang
    public boolean isBlank(final String s) {
        if (s == null) {
            return true;
        }
        Matcher m = PAT_BLANK.matcher(s);
        return m.matches();
    }
    
    public boolean isNotBlank(final String s) {
        return !isBlank(s);
    }
}
