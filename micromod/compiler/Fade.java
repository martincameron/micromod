
package micromod.compiler;

public class Fade implements Element {
	private Note parent;
	private TimeStretch sibling;
	
	public Fade( Note parent ) {
		this.parent = parent;
		sibling = new TimeStretch( parent );
	}
	
	public String getToken() {
		return "Fade";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		if( "Begin".equals( value ) ) {
			parent.beginFade();
		} else if( "End".equals( value ) ) {
			parent.endFade();
		} else {
			throw new IllegalArgumentException( "Invalid fade parameter (Begin or End): " + value );
		}
	}
	
	public void end() {
	}
}
