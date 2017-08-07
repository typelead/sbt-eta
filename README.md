# sbt-eta

An sbt plugin that overrides the build cycle to compile Etlas projects located in
`src/main/eta`. It also provides tasks for compiling & running Scala/Eta programs.

### Prerequisites

- [Etlas](https://github.com/typelead/etlas) >= 1.0.2.0
  - Make sure it's visible on the `PATH`.

    ```shell
    etlas --version
    ```

## Install

Add the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("com.typelead" % "sbt-eta" % "0.1.0")
```

## Example

An example project integration Eta with Scala is provided in the [example](./README.md)
directory.

## Basics

1. Create a standard Scala/SBT project using your favorite method.

2. Create the `src/main/eta` folder.

3. Run `etlas init` inside that folder to initialize an Etlas project.

4. Write Scala/Eta code to perform your task.

5. Start up `sbt` and run the `compile` and/or `run` task.

## Configuration

```scala
etaSource in Compile := (sourceDirectory in Compile).value / "something"
```
- **Type:** `File`
- **Default:** `src/main/eta`

This is the root of your Etlas project.

```scala
etaTarget := target.value / "something"
```

- **Type:** `File`
- **Default:** `target/eta/dist`

This is where all of Etlas's build artifacts are stored.

## License

`sbt-eta` is available under the BSD 3-Clause License, see [LICENSE](./LICENSE) for
more information.
