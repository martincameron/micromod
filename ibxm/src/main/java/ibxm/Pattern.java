package ibxm;

import micromod.AbstractPattern;
import micromod.Note;

public class Pattern extends AbstractPattern {
	private int numRows;

	public Pattern( int numChannels, int numRows ) {
		super( numChannels, numRows, 5 );
		this.numRows = numRows;
	}

	@Override
	public void getNote( int index, int channel, Note note ) {
		int offset = index * 5;
		note.key = patternData[ offset ] & 0xFF;
		note.instrument = patternData[ offset + 1 ] & 0xFF;
		note.volume = patternData[ offset + 2 ] & 0xFF;
		note.effect = patternData[ offset + 3 ] & 0xFF;
		note.parameter = patternData[ offset + 4 ] & 0xFF;
	}

	@Override
	public void setNote( int index, int channel, Note note ) {
		int offset = index * 5;
		patternData[ offset ] = ( byte ) note.key;
		patternData[ offset + 1 ] = ( byte ) note.instrument;
		patternData[ offset + 2 ] = ( byte ) note.volume;
		patternData[ offset + 3 ] = ( byte ) note.effect;
		patternData[ offset + 4 ] = ( byte ) note.parameter;
	}

	@Override
	public int getNumRows() {
		return numRows;
	}

}
