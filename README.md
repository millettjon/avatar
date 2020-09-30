# Simple avatar server.

## Run server:

``` shell
$ bin/run
```

## Build uberjar and run from docker.
``` shell
$ bin/docker-build
$ bin/docker-run
```

## View all images.
``` shell
$ xdg-open http://localhost:9090/gallery
```

## Upload test images.
``` shell
$ http --verbose -f POST http://localhost:9090/upload image@test/iroh.jpg
$ http --verbose -f POST http://localhost:9090/upload image@test/dog.png
```

## List all images as json.
``` shell
$ xdg-open http://localhost:9090/images
```

## Run tests and watch for changes.
``` shell
$ bin/kaocha-watch
```

## TODO
- factor out namespaces for
  - config
  - data access
  - image manipulations
- fix reflection warning
- add validation and error handling
  - spec? expound?
- add ring exception handler
- store data in postgres
- upload gif and convert to webp
