# caller-id

A simple interface for adding and retrieving users in a Caller ID database.

This is a proof of concept using Compojure to construct a simple REST API.

_Warning_: not production ready (of course), and data is not persisted.


## Running

Write/Unzip/Copy some data into
`resources/interview-callerid-data.csv` and make sure it's of the
format:

    <phone>,<name>,<context>

To start a web server for the application, run:

    lein ring server
    
or to set a port from the command line, simply set an environment variable

    PORT=3123 lein ring server

## Deployment

you can create a `.jar` file with:

    lein ring uberjar
    
and run _that_ with:

    java $JVM_OPTS -jar target/caller-id-standalone.jar
    
### On Heroku

make a file named `Procfile` at the root containing
web: java $JVM_OPTS -jar target/shouter-standalone.jar

## Testing it by hand

Add Joe Shmoe:

```
curl -i -H 'Content-Type: application/json' -XPOST 'http://localhost:3000/number/123-456-7890' -d '{
    "name"    : "Joe Shmoe",
    "phone"   : "123-456-7890",
    "context" : "yoyo competition"
}
'
```

And again in a different context:

```
curl -i -H 'Content-Type: application/json' -XPOST 'http://localhost:3000/number/123-456-7890' -d '{
    "name"    : "Joe Shmoe",
    "phone"   : "123-456-7890",
    "context" : "pokemon battle!!"
}
'
```

And then get em back out!

```
curl -i -H 'Content-Type: application/json' -XGET 'http://localhost:3000/query?number=%2B11234567890' 
```

## License

Copyright © 2017 Josh Freckleton
