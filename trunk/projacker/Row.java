package projacker;

public class Row implements Element {
	private int rowIdx;
	private Pattern parent;

	public Row( Pattern parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Row";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String row ) {
		int channelIdx = 0;
		char[] input = new char[ 8 ];
		micromod.Note output = new micromod.Note();
		int idx = 0, len = row.length();
		while( idx < len ) {
			int noteIdx = 0;
			while( idx < len && noteIdx < 8 ) {
				input[ noteIdx++ ] = row.charAt( idx++ );
			}
			if( noteIdx == 8 ) {
				output.fromString( new String( input ) );
				parent.setNote( rowIdx, channelIdx, output );
			} else {
				throw new IllegalArgumentException( "Pattern " + parent.getPatternIdx() + " Row " + rowIdx + " Channel " + channelIdx + ". Malformed key: " + new String( input, 0, noteIdx ) );
			}
			while( idx < len && row.charAt( idx ) <= 32 ) {
				idx++;
			}
			channelIdx++;							
		}
		rowIdx++;		
	}
	
	public void end() {
	}
	
	public void setRowIdx( int rowIdx ) {
		this.rowIdx = rowIdx;
	}	
}
