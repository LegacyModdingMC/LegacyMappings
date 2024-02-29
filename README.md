> NOTE: This repo is a work in progress, the following readme is a draft. 

# LegacyMappings

A set of mappings for Minecraft 1.7.10 that attempt to continue where MCP left off.

The repo was initialized by layering MCP on top of Feather on top of Yarn. Names from each mapping set are preserved in the comments next to mappings, allowing them to be easily compared and referenced.

## Details

The full set of mappings that were layered are the following (names on the right take priority over names on the left):
- `Legacy Yarn` < `Feather` < `MCP stable-39-1.12` < `MCP stable-32-1.11` < `MCP stable-29-1.10.2` < `MCP stable-26.1.9.4` < `MCP stable-22-1.8.9` < `MCP stable-12-1.7.10` < `MCP 1.7.10 (Forge userdev)`

The mappings are in the [Tiny V2 format](https://fabricmc.net/wiki/documentation:tiny2), split into a separate file for each class. A second destination namespace is used for comments.

The comments show the name of each element in the following four mappings:
- MCP↓ (layered, older preferred), MCP↑ (layered, newer preferred), Legacy Yarn, Feather

A `~` symbol means no mapping is present, and a `:` symbol means the previous name is repeated. For example:

```
#textureName, :, ~, spriteName
```

This means the field is called `textureName` in MCP↓, also `textureName` in MCP↑, nothing in Legacy Yarn, and `spriteName` in Feather.

## Usage

RetroFuturaGradle's support for custom mappings is [still a work in progress](https://github.com/GTNewHorizons/RetroFuturaGradle/issues/58), so using these mappings is not yet recommended for the general public. The adventurous ones can refer to the buildscript of this repo to see how it can be done.

## Contributing

Don't do it yet.

## Gradle tasks

### `enigma`

Opens Enigma for editing the mappings, using a custom fork that can display comments. This is the preferred way to edit the mappings, but sometimes editing the files manually may still be necessary.

### `build`

Builds the mappings in tiny-v2 and csv formats, and attempts to build the example mod using the mappings.

### `exportMappings`

Builds the mappings without testing.

### `generateMappings`

Generates a new set of mappings in the directory format. This will overwrite any changes! You will probably never want to run this, it's only supposed to be used to intitialize a branch.

### `generateDebugMappings`

Generates a new set of mappings in a single-file format which does not have the MCP layers flattened. This can be useful as reference. The mappings get written to a separate file so the source directory doesn't get modified.

## Credits

This project uses data from the following projects:

- [MCP](https://github.com/ModCoderPack/MCPMappingsArchive) (custom permissive license, see [MCP.LICENSE](MCP.LICENSE))
- [Legacy Yarn](https://github.com/Legacy-Fabric/yarn) ([CC0-1.0](LICENSE.CC0))
- [Feather](https://github.com/OrnitheMC/feather-mappings/) ([CC0-1.0](LICENSE.CC0))

Additionally, some of the buildscript code was taken from [Yarn](https://github.com/Legacy-Fabric/yarn)'s. ([CC0-1.0](LICENSE.CC0))

## License

This project maintains MCP's license for the data (the contents of the `mappings` directory, and any exported mappings). Everything else (the buildscript code) is under the [Unlicense](UNLICENSE).
