import java.nio.file.*;
class StateMoveProbe {
 public static void main(String[] args) throws Exception {
  Path dir=Files.createTempDirectory(Path.of("tmp"),"state-move-probe-");
  Path source=dir.resolve("new-state"), target=dir.resolve("service-state");
  try {
   Files.writeString(target,"PRUEBA"); Files.writeString(source,"BAJA");
   try {Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);System.out.println("MOVE_SUCCESS");}
   catch(Exception exception){System.out.println(exception.getClass().getName()+": "+exception.getMessage());}
   System.out.println("RESULT_STATE="+Files.readString(target));
  } finally {Files.deleteIfExists(source);Files.deleteIfExists(target);Files.deleteIfExists(dir);}
 }
}
