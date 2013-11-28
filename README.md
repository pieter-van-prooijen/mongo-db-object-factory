# JNDI Mongo DB Object Factory

This class allows for the specification of a Mongo DB object using the JNDI
(Java Naming and Directory Interface) tree, the same place where you would
configure a JDBC data source. This keeps the Mongo connection configuration
(hostname, database etc.) out of the web application and moves it to the
configuration of the application container.

The code was inspired by
[this stackoverflow sample](http://stackoverflow.com/questions/4076254/mongodb-via-jndi)
but doesn't use the Spring MongoTemplate class (although integration with
Spring is still possible) and adds write concerns and read preferences to
the configuration.

## Configuration
The object factory understands the following properties:

* _address_ The address:port for a connecting to a single mongod instance.
* _seeds_ A comma-separated list of addresses (with optional port) which specifies the seed list
  of a replica set. Either _address_ or _seeds_ is a required property.
* _database_ The database to create. (required).
* _username_ The username to connect to the database when authentication is
  configured for the database. The factory uses the Mongo
  challenge/response protocol.
* _password_ The password to use when authentication is configured for the database.
* _writeConcern_ The write concern to use when writing to the database. Use
  one of the strings defined in the Mongo WriteConcern class.
  (e.g. "majority" for the Majority write concern).
* _readPreference_ The read preference to use when connecting to a replica
  set. Use one of the strings defined in the ReadPreference class.

## Tomcat Configuration
Use this factory in Tomcat by adding this resource in the context.xml of a
webapp or in the global context file ($CATALINA_BASE/conf/context.ml).

The context.xml file can have following form:

    <?xml version='1.0' encoding='utf-8'?>
    <Context>
       <Resource name="mongo/commentsDB" auth="Container" type="com.mongo.DB"
	      factory="mongoutils.MongoDBObjectFactory"
		  address="some-mongodb-server" database="some-db-name"
          username="a-user" password="some-password"
          />
    </Context>

Note that this resource is instantiated on the first lookup, using the
class loader of the calling webapp. So this webapp should contain the
factory class and the mongo java driver. Alternatively, you can put them on
the shared tomcat classpath, for sharing between different web
applications. This behaviour is different from tomcat jdbc (connection
pool) resources which are instantiated when the container starts up and
which use the tomcat shared class loader.

The factory uses the slf4j logging library, so an appropriate adapter (e.g.
slf4j-log4j12) should be on the webapp's or tomcat's classpath.

## Retrieval Examples

Retrieve the manufactured object above using the following code:

    DB db = (DB) InitialContext.doLookup("java:comp/env/mongo/commentsDB");

or when using the Spring J2EE lookup mechanism:

    <beans xmlns="http://www.springframework.org/schema/beans"
	    xmlns:jee="http://www.springframework.org/schema/jee"
	    xsi:schemaLocation="http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
        http://www.springframework.org/schema/jee 
        http://www.springframework.org/schema/jee/spring-jee-3.0.xsd>

        <!-- jndi-lookup prefixes the search with java:comp/env -->
	    <jee:jndi-lookup id="commentsDB" jndi-name="mongo/commentsDB" />
    </beans>

The spring-data-mongo MongoTemplate class doesn't accept straight DB
instances, but uses a MongoDbOFactory interface, so
a very small wrapper or shim is needed around the DB object to make suitable for
use by this template:

    public class MongoDbFactoryWrapper implements MongoDbFactory {

    private DB db;
    
    public MongoDbFactoryWrapper(final DB db) {
        this.db = db;
    }
    
    @Override
    public DB getDb() throws DataAccessException {
        return db;
    }

    @Override
    public DB getDb(String dbName) throws DataAccessException {
        // This operation is not supported.
        return null;
    }
    }

Instantiate this wrapper in a straight forward manner in the Spring
configuration:

    <jee:jndi-lookup id="commentsDB" jndi-name="mongo/commentsDB" />

    <bean id="commentsMongoDbFactory" class="nl.rijksoverheid.platform.comments.common.service.springdata.MongoDbFactoryWrapper">
        <constructor-arg ref="commentsDB" />
	</bean>
	<bean id="mongoTemplate" class="org.springframework.data.mongodb.core.MongoTemplate">
		<constructor-arg ref="commentsMongoDbFactory" />
	</bean>

## Code

The code uses some Java 7 constructs (like switching on Strings). It logs
any configuration errors to slf4j. Note that some errors (like an unknown
ReadPreference string) will throw IllegalArgument exceptions.

For unit testing the Foursquare
[Fongo](https://github.com/foursquare/fongo) in-memory Mongo stub is used.

## Licence

Copyright © 2013 Pieter van Prooijen

Distributed under the MIT Licence.

