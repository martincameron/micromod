
package micros3m;

public class Pattern {
	public static final int NUM_ROWS = 64;
	public byte[] data;

	public void get_note( int index, Note note ) {
		int offset = index * 5;
		note.key = data[ offset ] & 0xFF;
		note.instrument = data[ offset + 1 ] & 0xFF;
		note.volume = data[ offset + 2 ] & 0xFF;
		note.effect = data[ offset + 3 ] & 0xFF;
		note.param = data[ offset + 4 ] & 0xFF;
	}
}
