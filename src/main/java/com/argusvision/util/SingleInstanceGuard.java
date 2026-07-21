package com.argusvision.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// So existe UMA webcam fisica por maquina, entao so pode existir UM
// ArgusVision rodando por maquina. Usa um FileLock do proprio sistema
// operacional: se o processo cair ou for morto, o lock e liberado
// automaticamente (diferente de um arquivo de PID, que pode ficar "preso"
// depois de um crash).
public class SingleInstanceGuard {

    private static FileChannel channel;
    private static FileLock lock;

    // Mantem a referencia do canal/lock viva pela duracao do processo -
    // se sair de escopo e for coletado pelo GC, o lock pode ser liberado cedo demais.
    public static boolean acquire() {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), "ArgusLogs");
            Files.createDirectories(dir);
            Path lockFile = dir.resolve("argusvision.lock");

            channel = new RandomAccessFile(lockFile.toFile(), "rw").getChannel();
            lock = channel.tryLock();
            return lock != null;
        } catch (IOException | OverlappingFileLockException e) {
            return false;
        }
    }
}
