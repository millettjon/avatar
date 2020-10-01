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
$ http --verbose -f POST http://localhost:9090/upload image@test/lighthouse.gif
$ http --verbose -f POST http://localhost:9090/upload image@test/zissou.webp
```

## View a single images.
``` shell
$ xdg-open http://localhost:9090/image/<UUID>-<SIZE>.<EXTENSION>
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
- fix reflection warning
- add ring exception handler
- store data in postgres
