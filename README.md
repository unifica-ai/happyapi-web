# HappyAPI Web

Like
- Code generation approach
  - documentation
  - completion
- Optional dependencies!

Questions

1. I want to use my own client with the generated libraries

   - It statically calls happyapi.provider.google
   - I can put something here, but then can I use `happyapi.provider.google` as well?
   - Perhaps: can I pass in a client in the generated code? Or a map with a client

2. Fns and deps in config are perfect when there is no default.
   For storage, there seems to be a sensible default implementation using the file system. 

   Perhaps fns for higher level stuff, and protocols for lower level?

4. I want to easily vary the middleware

  - Default middleware in a var, like in clj-http?
  
5. There are a few places where it really wants me to choose a web server,
   even though I'm not using it.

I set a nonsense function in `happyapi.edn`, because I don't use Jetty:

```
:fns             {:run-server web.happyapi/start} ;; don't want to run this!
```

I added a context to the arguments so that I could save the token to the DB.

I wanted a function to load the config with its functions, so I could look at it in the REPL and put it in the context map

Seems like having its own config loading may not be what I want. 

- is aero or a config loading library an optional dependency?

- is component, mount, integrant, or Biff-style 'modules' an optinal thign?  Should take a context map? 

Biff reloads code using tools.ns. This means a few files that aren't supposed to be compiled,
are compiled.

Problems reloading:

happyapi
- happyapi.deps.jetty

happyapi.google
- content_v2.1.clj
- happyapi.google.androidpublisher-v3 (syntax error)
- happyapi.google.adsensehost-v4.1

maybe it's the ones with the dots? Plus androidpublisher, which seems to have a syntax error

Metering
- I'm planning to use Temporal for this.
- But I guess you could use a resilience library for it
