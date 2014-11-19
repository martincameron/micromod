
package micromod.compiler;

public class Repeat implements Element {
	private Note parent;
	
	public Repeat( Note parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Repeat";
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
	
	public void begin( String value ) {
		if( "Begin".equals( value ) ) {
			parent.setRepeat( 1 );
		} else {
			parent.setRepeat( Parser.parseInteger( value ) );
		}
	}

	public void end() {
	}
}
