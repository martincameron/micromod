
package micromod.tracker;

public class FadeOut implements Element {
	private Note parent;
	
	public FadeOut( Note parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "FadeOut";
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
		parent.setFadeOut( Parser.parseInteger( value ) );
	}
	
	public void end() {
	}	
}
