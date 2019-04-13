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

```scala
addSbtPlugin("com.typelead" % "sbt-eta" % "0.3.0")
```

## Example

An example project integration Eta with Scala is provided in the [example](./example/README.md)
directory.

## Basics

1. Create a standard Scala/SBT project using your favorite method.

2. Initialize new Eta project one of two ways: 
 
   1. Manual:
  
      1. Create the `src/main/eta` folder.

      2. Run `etlas init` inside that folder to initialize an Etlas project.
   
   2. Automatically with sbt command:
 
      1. Run `eta-init` command in `sbt`, e.g. `sbt eta-init`.
      
      2. New Eta project initialized in the `src/main/eta` folder.

4. Write Scala/Eta code to perform your task.

5. Start up `sbt` and run the `compile` and/or `run` task.

## Configuration

```scala
etaPackageDir := baseDirectory.value
```
- **Type:** `File`
- **Default:** `src/main/eta`. If `EtaLayoutPlugin` is disabled: `.` (`baseDirectory`)

This is the root of your Etlas project.

```scala
etaSource in Compile := (sourceDirectory in Compile).value / "something"
```
- **Type:** `File`
- **Default:** `src/main/eta`

This is a directory containing sources of your Etlas project.

```scala
etaTarget := target.value / "something"
```

- **Type:** `File`
- **Default:** `target/eta/dist`

This is where all of Etlas's build artifacts are stored.

## Project layout

By default the directory structure of project looks like this:

```
   example/
   ├── Setup.hs
   └── src/
       └── main/
           ├── eta/
           │   ├── Example/
           │   |   ├── Transform.hs
           |   |   └── TransformTest.hs
           |   └── example.cabal
           └── java/
               └── Main.java
```

You also have the option of using a layout like the default one used by SBT and Maven. 
Please note that this layout is experimental and may have issues. 
In order to use this layout, you must disable the layout plugin in your `sbt` config:

```scala
disablePlugins(EtaLayoutPlugin)
```

After that the directory structure of project looks like this:

```       
   example/
   ├── example.cabal
   ├── Setup.hs
   └── src/
       ├── main/
       │   ├── eta/
       │   │   └── Example/
       │   │       └── Transform.hs
       │   └── java/
       │       └── Main.java
       └── test/
           └── eta/
               └── Example/
                   └── TransformTest.hs
``` 

## License

`sbt-eta` is available under the BSD 3-Clause License, see [LICENSE](./LICENSE) for
more information.
