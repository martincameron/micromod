
package micromod.compiler;

public class Begin implements Element {
	private Note parent;
	
	public Begin( Note parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Begin";
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
		parent.beginEffect( value );
	}

	public void end() {
	}
}
