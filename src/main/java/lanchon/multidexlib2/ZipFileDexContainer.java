/*
 * multidexlib2 - Copyright 2015-2020 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * multidexlib2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package lanchon.multidexlib2;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.ZipDexContainer;

public class ZipFileDexContainer extends AbstractMultiDexContainer<DexBackedDexFile> {

	public static boolean isZipFile(File zip) {
		if (!zip.isFile()) return false;
		try {
			ZipFile zipFile = new ZipFile(zip);
			zipFile.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public ZipFileDexContainer(File zip, DexFileNamer namer, Opcodes opcodes) throws IOException {
		Map<String, DexEntry<DexBackedDexFile>> entryMap = new TreeMap<>(new DexFileNameComparator(namer));
		ZipDexContainer zipDexContainer = new ZipDexContainer(zip, opcodes, false);
		List<String> dexEntryNames = zipDexContainer.getDexEntryNames();
		for (String dexEntryName : dexEntryNames) {
			if (namer.isValidName(dexEntryName)) {
				DexBackedDexFile dexFile = zipDexContainer.getEntry(dexEntryName).getDexFile();
				DexEntry<DexBackedDexFile> entry = new BasicDexEntry<>(this, dexEntryName, dexFile);
				if (entryMap.put(dexEntryName, entry) != null) throw duplicateEntryName(dexEntryName);
				continue;
			}
			String entryName = normalizeEntryName(dexEntryName, namer);
			if (entryName == null) continue;
			DexBackedDexFile dexFile = zipDexContainer.getEntry(dexEntryName).getDexFile();
			DexEntry<DexBackedDexFile> entry = new BasicDexEntry<>(this, entryName, dexFile);
			if (entryMap.put(entryName, entry) != null) throw duplicateEntryName(entryName);
		}
		initialize(entryMap);
	}

	private static String normalizeEntryName(String dexEntryName, DexFileNamer namer) {
		int separator = dexEntryName.lastIndexOf('/');
		if (separator < 0) return null;
		int parentIndex = namer.getIndex(dexEntryName.substring(0, separator));
		if (parentIndex < 0) return null;
		String memberIndexString = dexEntryName.substring(separator + 1);
		try {
			int memberIndex = Integer.parseInt(memberIndexString);
			if (memberIndex < 0 || !memberIndexString.equals(String.valueOf(memberIndex))) return null;
			return namer.getName(parentIndex + memberIndex);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

}
