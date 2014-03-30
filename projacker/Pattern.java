package projacker;

public class Pattern implements Element {
	private Module parent;
	private Row child = new Row( this );
	private micromod.Pattern pattern;
	private int patternIdx;

	public Pattern( Module parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Pattern";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		System.out.println( getToken() + ": " + value );
		patternIdx = Parser.parseInteger( value );
		pattern = parent.getPattern( patternIdx );
		child.setRowIdx( 0 );
	}
	
	public void end() {
		/* Expand macros.*/
		micromod.Note note = new micromod.Note();
		int numChannels = pattern.getNumChannels();
		for( int channelIdx = 0; channelIdx < numChannels; channelIdx++ ) {
			micromod.Pattern macro = null;
			int rowIdx = 0;
			int macroRowIdx = 0, transpose = 0, volume = 64;
			while( rowIdx < micromod.Pattern.NUM_ROWS ) {
				pattern.getNote( rowIdx, channelIdx, note );
				if( note.instrument > 0 ) {
					macro = parent.getMacro( note.instrument );
					macroRowIdx = 0;
					transpose = ( note.key > 0 ) ? note.key - 25 : 0;
					volume = ( note.effect == 0xC ) ? note.parameter : 64;
				}
				if( macro != null ) {
					macro.getNote( macroRowIdx++, 0, note );
					note.transpose( transpose, volume, parent.getModule() );
				}
				pattern.setNote( rowIdx++, channelIdx, note );
			}
		}
	}
	
	public int getPatternIdx() {
		return patternIdx;
	}
	
	public void setNote( int rowIdx, int channelIdx, micromod.Note note ) {
		pattern.setNote( rowIdx, channelIdx, note );
	}
}
