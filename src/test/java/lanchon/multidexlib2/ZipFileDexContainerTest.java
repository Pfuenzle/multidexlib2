package lanchon.multidexlib2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.MethodHandleType;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.raw.CodeItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableCallSiteReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodHandleReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodProtoReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipFileDexContainerTest {

    @Test
    public void readsSmaliDexContainerEntries() throws Exception {
        byte[] dex = writeSimpleDex("Ltest/Container;");
        byte[] container = concatenateContainer(dex, dex);
        File zip = writeZip("multidex-container", "classes.dex", container);

        ZipFileDexContainer dexContainer =
                new ZipFileDexContainer(zip, new BasicDexFileNamer(), Opcodes.forDexVersion(41));
        List<String> names = dexContainer.getDexEntryNames();
        assertEquals(2, names.size());
        assertEquals("classes.dex", names.get(0));
        assertEquals("classes2.dex", names.get(1));
        assertEquals(1, dexContainer.getEntry(names.get(0)).getDexFile().getClasses().size());
        assertEquals(1, dexContainer.getEntry(names.get(1)).getDexFile().getClasses().size());
    }

    @Test
    public void preservesStandaloneDexNamesBesideDexContainer() throws Exception {
        byte[] dex = writeSimpleDex("Ltest/Mixed;");
        File zip = File.createTempFile("multidex-mixed", ".apk");
        zip.deleteOnExit();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            writeEntry(output, "classes3.dex", dex);
            writeEntry(output, "classes.dex", concatenateContainer(dex, dex));
        }

		ZipFileDexContainer dexContainer =
				new ZipFileDexContainer(zip, new BasicDexFileNamer(), Opcodes.forDexVersion(41));
		assertEquals(3, dexContainer.getDexEntryNames().size());
		assertNotNull(dexContainer.getEntry("classes.dex"));
		assertNotNull(dexContainer.getEntry("classes2.dex"));
		assertNotNull(dexContainer.getEntry("classes3.dex"));
    }

    @Test
    public void writesInvokeCustomOutsSizeForIssue39() throws Exception {
        ImmutableCallSiteReference callSite = new ImmutableCallSiteReference(
                "call_site", new ImmutableMethodHandleReference(MethodHandleType.INVOKE_STATIC,
                        new ImmutableMethodReference("Ltest/CallSite;", "bootstrap",
                                ImmutableList.of("Ljava/lang/invoke/Lookup;", "Ljava/lang/String;",
                                        "Ljava/lang/invoke/MethodType;"),
                                "Ljava/lang/invoke/CallSite;")),
                "target", new ImmutableMethodProtoReference(ImmutableList.of(), "V"), ImmutableList.of());
        ImmutableMethod method = new ImmutableMethod("Ltest/CallSite;", "invoke", ImmutableList.of(), "V",
                AccessFlags.PUBLIC.getValue() | AccessFlags.STATIC.getValue(), null, null,
                new ImmutableMethodImplementation(1,
                        ImmutableList.of(new ImmutableInstruction35c(Opcode.INVOKE_CUSTOM, 1,
                                0, 0, 0, 0, 0, callSite)), null, null));
        ImmutableClassDef classDef = new ImmutableClassDef("Ltest/CallSite;", AccessFlags.PUBLIC.getValue(),
                "Ljava/lang/Object;", null, null, ImmutableSet.of(), null, ImmutableList.of(method));
        File dex = File.createTempFile("invoke-custom", ".dex");
        dex.deleteOnExit();
        DexPool.writeTo(new FileDataStore(dex), new ImmutableDexFile(Opcodes.forArtVersion(111),
                ImmutableSet.of(classDef)));

        int codeOffset = findOnlyCodeOffset(dex);
		try (RandomAccessFile input = new RandomAccessFile(dex, "r")) {
			input.seek(codeOffset + CodeItem.OUTS_OFFSET);
			assertEquals(1, readUnsignedShort(input));
        }
    }

    @Test
    public void readsLegacyStandaloneDexEntries() throws Exception {
        File zip = writeZip("multidex-standalone", "classes.dex", writeSimpleDex("Ltest/Standalone;"));
        ZipFileDexContainer dexContainer =
                new ZipFileDexContainer(zip, new BasicDexFileNamer(), Opcodes.getDefault());
        assertNotNull(dexContainer.getEntry("classes.dex"));
        assertEquals(1, dexContainer.getDexEntryNames().size());
    }

    private static byte[] writeSimpleDex(String type) throws Exception {
        ImmutableClassDef classDef = new ImmutableClassDef(
                type, AccessFlags.PUBLIC.getValue(), "Ljava/lang/Object;", null, null,
                ImmutableSet.of(), null, null);
        MemoryDataStore dataStore = new MemoryDataStore();
        DexPool.writeTo(dataStore, new ImmutableDexFile(Opcodes.getDefault(), ImmutableSet.of(classDef)));
        return dataStore.getData();
    }

    private static byte[] concatenateContainer(byte[] first, byte[] second) {
        byte[] container = new byte[first.length + second.length];
        System.arraycopy(first, 0, container, 0, first.length);
        System.arraycopy(second, 0, container, first.length, second.length);
        for (int offset : new int[] {0, first.length}) {
            container[offset + 4] = '0';
            container[offset + 5] = '4';
            container[offset + 6] = '1';
            writeInt(container, offset + HeaderItem.CONTAINER_SIZE_OFFSET, container.length);
            writeInt(container, offset + HeaderItem.HEADER_OFFSET_OFFSET, offset);
        }
        return container;
    }

    private static File writeZip(String prefix, String entryName, byte[] bytes) throws Exception {
        File zip = File.createTempFile(prefix, ".apk");
        zip.deleteOnExit();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            writeEntry(output, entryName, bytes);
        }
        return zip;
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
	private static int readUnsignedShort(RandomAccessFile input) throws Exception {
		int low = input.readUnsignedByte();
		return low | (input.readUnsignedByte() << 8);
	}
	private static int findOnlyCodeOffset(File dex) throws Exception {
		try (RandomAccessFile input = new RandomAccessFile(dex, "r")) {
			int classDefOffset = readInt(input, HeaderItem.CLASS_START_OFFSET);
			int classDataOffset = readInt(input, classDefOffset + 24);
			input.seek(classDataOffset);
			int staticFields = readUleb128(input);
			int instanceFields = readUleb128(input);
			int directMethods = readUleb128(input);
			int virtualMethods = readUleb128(input);
			for (int i = 0; i < staticFields + instanceFields; i++) {
				readUleb128(input);
				readUleb128(input);
			}
			int methodCount = directMethods + virtualMethods;
			int methodIndex = 0;
			int codeOffset = 0;
			for (int i = 0; i < methodCount; i++) {
				methodIndex += readUleb128(input);
				readUleb128(input);
				codeOffset = readUleb128(input);
			}
			if (methodCount != 1 || codeOffset == 0) throw new AssertionError("Expected one code item");
			return codeOffset;
		}
	}

	private static int readInt(RandomAccessFile input, int offset) throws Exception {
		input.seek(offset);
		return Integer.reverseBytes(input.readInt());
	}

    private static int readUleb128(RandomAccessFile input) throws Exception {
        int result = 0;
        int shift = 0;
        while (true) {
            int value = input.readUnsignedByte();
            result |= (value & 0x7f) << shift;
            if ((value & 0x80) == 0) return result;
            shift += 7;
        }
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
