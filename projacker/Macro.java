
package projacker;

public class Macro {
	private String scale, root;
	private micromod.Pattern notes;
	
	public Macro( String scale, String root, micromod.Pattern notes ) {
		this.scale = ( scale != null ) ? scale : "C#D#EF#G#A#B";
		this.root = ( root != null ) ? root : "C-2" ;
		this.notes = notes;
	}
	
	public void expand( Pattern pattern, int channelIdx, int rowIdx ) {
		// Expand macro into Pattern until end or another note is set.
	}
	
	public void getNote( int rowIdx, micromod.Note note ) {
		// Temporary until expand() implemented.
		notes.getNote( rowIdx, 0, note );
	}
}
