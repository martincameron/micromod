
package micromod;

public class Macro {
	private Scale scale;
	private int rootKey;
	private Pattern notes;
	
	public Macro( String scale, String rootKey, Pattern notes ) {
		this.scale = new Scale( scale != null ? scale : Scale.CHROMATIC );
		this.rootKey = Note.parseKey( rootKey != null ? rootKey : "C-2" );
		this.notes = new Pattern( 1, notes );
	}

	/* Return a new Macro with the notes transposed to the specified key.*/
	public Macro transpose( String key ) {
		int semitones = Note.parseKey( key ) - rootKey;
		Pattern pattern = new Pattern( 1, notes );
		Note note = new Note();
		for( int rowIdx = 0; rowIdx < 64; rowIdx++ ) {
			pattern.getNote( rowIdx, 0, note );
			note.transpose( semitones, 64, null );
			pattern.setNote( rowIdx, 0, note );
		}
		return new Macro( scale.transpose( semitones ), key, pattern );
	}

	/* Expand macro into the specified pattern until end or an instrument is set. */
	public int expand( Module module, int patternIdx, int channelIdx, int rowIdx ) {
		return expand( module, new int[] { patternIdx }, channelIdx, rowIdx );
	}

	/* Treat the specified patterns as a single large pattern and expand macro
	   until the end or an instrument is set. The final row index is returned. */
	public int expand( Module module, int[] patterns, int channelIdx, int rowIdx ) {
		int macroRowIdx = 0, srcKey = rootKey, distance = 0, volume = 64;
		Note note = new Note();
		while( macroRowIdx < Pattern.NUM_ROWS ) {
			int patternsIdx = rowIdx / Pattern.NUM_ROWS;
			if( patternsIdx >= patterns.length ) {
				break;
			}
			Pattern pattern = module.getPattern( patterns[ patternsIdx ] );
			pattern.getNote( rowIdx % Pattern.NUM_ROWS, channelIdx, note );
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
			}
			int dstKey = scale.transpose( srcKey, distance );
			int semitones = dstKey - srcKey;
			if( effect > 0 ) {
				if( effect == 0xC ) {
					volume = param;
				}
				if( effect != 0xC || param == 0 ) {
					note.effect = effect;
					note.parameter = param;
				}
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
			pattern.setNote( ( rowIdx++ ) % pattern.NUM_ROWS, channelIdx, note );
		}
		return rowIdx;
	}
}
