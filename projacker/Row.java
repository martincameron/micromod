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
		String[] notes = Parser.split( row, ' ' );
		micromod.Note output = new micromod.Note();
		for( int chanIdx = 0; chanIdx < notes.length; chanIdx++ ) {
			try {
				output.fromString( notes[ chanIdx ] );
				parent.setNote( rowIdx, chanIdx, output );
			} catch( IllegalArgumentException e ) {
				String msg = "Pattern " + parent.getPatternIdx() + " Row " + rowIdx + " Channel " + chanIdx;
				throw new IllegalArgumentException( msg + " " + e.getMessage() );
			}
		}
		rowIdx++;
	}
	
	public void end() {
	}
	
	public void setRowIdx( int rowIdx ) {
		this.rowIdx = rowIdx;
	}	
}
