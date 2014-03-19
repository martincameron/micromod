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
	}
	
	public int getPatternIdx() {
		return patternIdx;
	}
	
	public void setNote( int rowIdx, int channelIdx, micromod.Note note ) {
		pattern.setNote( rowIdx, channelIdx, note );
	}
}
