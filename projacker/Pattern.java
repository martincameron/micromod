package projacker;

public class Pattern implements Element {
	private Module parent;
	private Row child = new Row( this );
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
		child.setRowIdx( 0 );
	}
	
	public void end() {
		/* Expand macros.*/
		micromod.Pattern pattern = parent.getPattern( patternIdx );
		micromod.Note note = new micromod.Note();
		int numChannels = pattern.getNumChannels();
		for( int channelIdx = 0; channelIdx < numChannels; channelIdx++ ) {
			for( int rowIdx = 0; rowIdx < micromod.Pattern.NUM_ROWS; rowIdx++ ) {
				pattern.getNote( rowIdx, channelIdx, note );
				if( note.instrument > 0 ) {
					micromod.Macro macro = parent.getMacro( note.instrument );
					if( macro != null ) {
						note.instrument = 0;
						pattern.setNote( rowIdx, channelIdx, note );
						macro.expand( parent.getModule(), patternIdx, channelIdx, rowIdx );
					}
				}
			}
		}
	}
	
	public int getPatternIdx() {
		return patternIdx;
	}
	
	public void setNote( int rowIdx, int channelIdx, micromod.Note note ) {
		parent.getPattern( patternIdx ).setNote( rowIdx, channelIdx, note );
	}
}
