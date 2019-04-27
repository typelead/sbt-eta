# sbt-eta

[![Build Status](https://circleci.com/gh/typelead/sbt-eta.svg?style=shield)](https://circleci.com/gh/typelead/sbt-eta)

An sbt plugin that overrides the build cycle to compile Etlas projects located in
`src/main/eta`. It also provides tasks for compiling & running Scala/Eta programs.

### Prerequisites

- [Etlas](https://github.com/typelead/etlas) >= 1.5.0.0
  - Make sure it's visible on the `PATH`.

    ```shell
    etlas --version
    ```

## Install

Add the following to your `project/plugins.sbt` file:

```sbtshell
addSbtPlugin("com.typelead" % "sbt-eta" % "0.3.0")
```

For projects using Eta enable SbtEta plugin, e.g.:

```sbtshell
val root = (project in file(".")).enablePlugins(SbtEta)
```

## Example

An example project integration Eta with Scala is provided in the [example](./example/README.md) directory.

An example of multi-project build included Eta projects is provided in the [example-multi](./example-multi/README.md) directory.

## Basics

1. Create a standard Scala/SBT project using your favorite method.

2. Describe Eta project in `build.sbt` using plugin's DSL. 

3. Write Scala/Eta code to perform your task.

4. Start up `sbt` and run the `compile` and/or `run` task.

## Configuration

```sbtshell
baseDirectory in Eta := target.value / "eta"
```
- **Type:** `File`
- **Default:** `target/eta`

This is the root of your Etlas project (where the `.cabal` file is placed).

```sbtshell
target in Eta := target.value / "eta" / "dist"
```

- **Type:** `File`
- **Default:** `target/eta/dist`

This is where all of Etlas's build artifacts are stored.

```sbtshell
sourceDirectory in EtaLib := (sourceDirectory in Compile).value / "eta"
```
- **Type:** `File`
- **Default:** `src/main/eta`

This is a directory containing sources of your Etlas project.

TODO

## Project layout

By default the directory structure of project looks like the default one used by SBT and Maven:

```       
   example/
   ├── build.sbt
   ├── src/
   |   ├── main/
   |   │   ├── eta/
   |   │   │   └── Example/
   |   │   │       └── Transform.hs
   |   │   └── java/
   |   │       └── Main.java
   |   └── test/
   |       └── eta/
   |           └── Example/
   |               └── TransformTest.hs
   └── target/
       └── eta/
           ├── dist/
           └── example-eta.cabal
``` 

## License

`sbt-eta` is available under the BSD 3-Clause License, see [LICENSE](./LICENSE) for
more information.
