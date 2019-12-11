# CloMon

CloMon is a personal service monitor and network tool supporting http/s and ping checks, as well as Wake-On-Lan events.

All checks should be defined in a `config.edn` file within the resources folder (or elsewhere on the JVM classpath).
There is an example `test.edn` provided.
Currently, the `config.edn` is included in the `.gitignore` file to prevent credential and infra leaks.

## Requirements

This project uses Clojure's [CLI tools](https://clojure.org/guides/getting_started), with PostgreSQL as backend.

## Usage

Run the project directly:

    $ clj -m clomon.main
    
You should see log results in the console as well as the specified logfile.
CloMon provides a web based viewer for the results which can be accessed from [localhost:8081/clomon](http://localhost:8081/clomon).

You can also create an uberjar with:

    $ clojure -A:depstar

### Issues

Ping supports needs special permissions on Linux, so either run as root or set the systemd service capabilities below.

```bash
setcap cap_net_raw=ep cap_net_admin=ep
```

For systemd service:

```conf
AmbientCapabilities=CAP_NET_RAW+ep
Capabilities=CAP_NET_BIND_SERVICE+ep
CapabilityBoundingSet=...
```

## Testing

Run the project's tests:

    $ clj -A:test:runner

## License

Copyright © 2019 Bor Hodošček

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
