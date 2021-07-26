package avian.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Files {
  private static final boolean IsWindows
    = System.getProperty("os.name").equals("Windows");

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }
  
  private static void isAbsoluteTest(boolean absolutePath) {
    File file = new File("test.txt");
    if (absolutePath) {
      file = file.getAbsoluteFile();
    }
    
    boolean isAbsolute = file.isAbsolute();
    
    if (absolutePath) {
      expect(isAbsolute);
    } else {
      expect(!isAbsolute);
    }
    
  }

  private static void isRootParent() {
    if(!IsWindows) {
      File f = new File("/root");
      File f2 = f.getParentFile();
      System.out.println("------------"+f2);
      expect(f2.getPath().equals("/"));
    }
  }

  private static void setExecutableTestWithPermissions(boolean executable)
    throws Exception
  {
    File file = File.createTempFile("avian.", null);
    try {
      file.setExecutable(executable);
      if (executable) {
        expect(file.canExecute());
      } else {
        // Commented out because this will fail on Windows - both on Avian and on OpenJDK
        // The implementation for Windows considers canExecute() to be the same as canRead()
        // expect(!file.canExecute());
      }
    } finally {
      expect(file.delete());
    }
  }
  
  public static void main(String[] args) throws Exception {
    isAbsoluteTest(true);
    isAbsoluteTest(false);
    setExecutableTestWithPermissions(true);
    setExecutableTestWithPermissions(false);
    isRootParent();
  
    { File f = new File("test.txt");
      f.createNewFile();
      expect(! f.createNewFile());
      f.delete();
    }

    { File f = new File("test.txt");
      FileOutputStream out = new FileOutputStream(f);
      try {
        byte[] message = "hello, world!\n".getBytes();
        out.write(message);
        out.close();

        expect(f.lastModified() > 0);

        FileInputStream in = new FileInputStream(f);
        try {
          expect(in.available() == message.length);
          
          for (int i = 0; i < message.length; ++i) {
            in.read();
            expect(in.available() == message.length - i - 1);
          }
          
          expect(in.read() == -1);
          expect(in.available() == 0);
        } finally {
          in.close();
        }
      } finally {
        f.delete();
      }
    }

    if(IsWindows) {
      expect(new File("/c:\\test").getPath().equals("c:\\test"));
    } else {
      expect(new File("/c:\\test").getPath().equals("/c:\\test"));
    }

    expect(new File("foo/bar").getParent().equals("foo"));
    expect(new File("foo/bar/").getParent().equals("foo"));
    expect(new File("foo/bar//").getParent().equals("foo"));

    expect(new File("foo/nonexistent-directory").listFiles() == null);
  }

}
