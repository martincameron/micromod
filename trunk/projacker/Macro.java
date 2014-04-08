
package projacker;

public class Macro {
	private Scale scale;
	private int rootKey;
	private micromod.Pattern notes;
	
	public Macro( String scale, String root, micromod.Pattern notes ) {
		this.scale = new Scale( scale != null ? scale : Scale.CHROMATIC );
		this.rootKey = micromod.Note.parseKey( root != null ? root : "C-2" );
		this.notes = notes;
	}
	
	public void expand( Pattern pattern, int channelIdx, int rowIdx ) {
		// Expand macro into Pattern until end or another note is set.
	}
	
	public void getNote( int rowIdx, micromod.Note note ) {
		// Temporary until expand() implemented.
		notes.getNote( rowIdx, 0, note );
	}
	public int getTranspose( int key ) {
		return scale.getDistance( rootKey, key );
	}
	public void transpose( micromod.Note note, int distance, int volume, micromod.Module module ) {
		int semitones = 0;
		if( note.key > 0 ) {
			semitones = scale.transpose( note.key, distance ) - note.key;
		}
		note.transpose( semitones, volume, module );
	}
}
