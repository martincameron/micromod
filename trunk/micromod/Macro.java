
package micromod;

public class Macro {
	private Scale scale;
	private int rootKey;
	private Pattern notes;
	
	public Macro( String scale, String root, Pattern notes ) {
		this.scale = new Scale( scale != null ? scale : Scale.CHROMATIC );
		this.rootKey = Note.parseKey( root != null ? root : "C-2" );
		this.notes = notes;
	}
	
	/* Expand macro into Pattern until end or an instrument is set. */
	public void expand( Module module, int patternIdx, int channelIdx, int rowIdx ) {
		int macroRowIdx = 0, srcKey = 0, dstKey = 0, distance = 0, volume = 64;
		Pattern pattern = module.getPattern( patternIdx );
		Note note = new Note();
		while( rowIdx < Pattern.NUM_ROWS ) {
			pattern.getNote( rowIdx, channelIdx, note );
			if( note.instrument > 0 ) {
				break;
			}
			if( note.key > 0 ) {
				distance = scale.getDistance( rootKey, note.key );
			}
			int effect = note.effect;
			int param = note.parameter;
			notes.getNote( macroRowIdx++, 0, note );
			if( note.key > 0 ) {
				srcKey = note.key;
				dstKey = scale.transpose( srcKey, distance );
			}
			int semitones = 0;
			if( dstKey > 0 ) {
				semitones = dstKey - srcKey;
			}
			if( effect == 0xC ) {
				volume = param;
			} else if( ( note.effect | note.parameter ) == 0 ) {
				note.effect = effect;
				note.parameter = param;
			}
			note.transpose( semitones, volume, module );
			if( semitones != 0 && note.effect == 0 && note.parameter != 0 ) {
				/* Adjust arpeggio.*/
				int dist = scale.getDistance( srcKey, srcKey + ( ( note.parameter >> 4 ) & 0xF ) );
				int arp1 = scale.transpose( dstKey, dist ) - dstKey;
				if( arp1 < 0 ) arp1 = 0;
				if( arp1 > 15 ) arp1 = ( arp1 - 3 ) % 12 + 3;
				dist = scale.getDistance( srcKey, srcKey + ( note.parameter & 0xF ) );
				int arp2 = scale.transpose( dstKey, dist ) - dstKey;
				if( arp2 < 0 ) arp2 = 0;
				if( arp2 > 15 ) arp2 = ( arp2 - 3 ) % 12 + 3;
				note.parameter = ( arp1 << 4 ) + ( arp2 & 0xF );
			}
			pattern.setNote( rowIdx++, channelIdx, note );
		}
	}
}
