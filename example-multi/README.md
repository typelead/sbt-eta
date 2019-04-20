# Multi-Project Example

This example demonstrates how to setup a complex multi-project build.

## Dependency Layout

```
java-deep <-- hio <-- java-shallow
              ^           ^
               \         /
                \       /
                  multi
```

## Running the example

```
$ sbt multi/run
```
