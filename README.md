## multidexlib2
### Multi-dex extensions for Google Smali dexlib2.

This is the Pfuenzle-maintained DexWeaver fork, updated to Google Smali 3.0.10
(`com.android.tools.smali:smali-dexlib2`). ZIP DEX containers with entry names such as
`classes.dex/0` and `classes.dex/1` are normalized to the configured `DexFileNamer`; standalone
entries retain their original names.
- Multi-dex reading and writing.
- Optional management of the content of the main dex file when writing multi-dex.
- Simplified read/write access to multi-dex containers as plain-old dexlib2 `DexFile` objects, making it trivial to add multi-dex support to existing non-multi-dex-aware dexlib2 clients.
- Faster dex reads than plain dexlib2.
- Optional multi-threaded multi-dex writes that deliver greatly increased write performance.
- Automatic management of dex version, dex opcodes, and API level.
- Configurable naming of multi-dex member files.
- Progress logging to a client-provided logger.

Limitations:
- Only supports dex files. (Does not support odex/oat files.)
- Only writes dex files to directories. (Does not write into zip files.)

### DISTRIBUTION

The workspace builds local Maven artifacts under `io.pfuenzle.dexweaver:multidexlib2:3.0.10.r3`.

### USAGE

Interface:
- [__MultiDexIO:__](https://github.com/Pfuenzle/multidexlib2/blob/master/src/main/java/lanchon/multidexlib2/MultiDexIO.java) reads and writes potentially multi-dex containers.
  - __readDexFile():__ reads potentially multi-dex container and returns a merged `DexFile`.
  - __readMultiDexContainer():__ reads potentially multi-dex container and returns a `MultiDexContainer`.
  - __writeDexFile():__ writes potentially multi-dex container from a single `DexFile`.
  - __RawDexIO:__ reads and writes single dex files.

Parameters:
- __boolean multiDex:__ `true` to enable multi-dex support.
- __File file:__ file or directory to read or write.
- __DexFileNamer namer:__ set to `new BasicDexFileNamer()`.
- __Opcodes opcodes:__ `null` to auto-detect.
- __int maxDexPoolSize:__ set to `DexIO.DEFAULT_MAX_DEX_POOL_SIZE`.
- __int threadCount:__ thread count for multi-threaded multi-dex writes.
  - `1` to force single-threaded writes.
  - `0` to auto-detect optimum thread count.
- __int minMainDexClassCount, boolean minimalMainDex:__ main dex file content management.
  - `0, false` to disable main dex management.
- __[DexIO](https://github.com/DexPatcher/multidexlib2/blob/master/src/main/java/lanchon/multidexlib2/DexIO.java).Logger logger:__ `null` to disable logging.

Sample:

DexWeaver's source processor is a production client of this library.
