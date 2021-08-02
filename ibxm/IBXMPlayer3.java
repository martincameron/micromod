
package ibxm;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class IBXMPlayer3 extends Canvas implements KeyListener, MouseListener, MouseMotionListener, WindowListener
{
	private static final String[] KEYS = new String[]
	{
		"F1-F4: Keyboard transpose.",
		"F5: Toggle reverb effect.",
		"F6: No interpolation filter.",
		"F7: Linear interpolation filter.",
		"F8: Sinc interpolation filter.",
		"Q-P: Keyboard octave 1.",
		"Z-M: Keyboard octave 2.",
		"Shift: Sustain notes.",
		"Space: All notes off."
	};
	
	private static final long[] TOPAZ_8 = new long[]
	{
		0x0000000000000000L,
		0x1818181818001800L,
		0x6C6C000000000000L,
		0x6C6CFE6CFE6C6C00L,
		0x183E603C067C1800L,
		0x0066ACD8366ACC00L,
		0x386C6876DCCE7B00L,
		0x1818300000000000L,
		0x0C18303030180C00L,
		0x30180C0C0C183000L,
		0x00663CFF3C660000L,
		0x0018187E18180000L,
		0x0000000000181830L,
		0x0000007E00000000L,
		0x0000000000181800L,
		0x03060C183060C000L,
		0x3C666E7E76663C00L,
		0x1838781818181800L,
		0x3C66060C18307E00L,
		0x3C66061C06663C00L,
		0x1C3C6CCCFE0C0C00L,
		0x7E607C0606663C00L,
		0x1C30607C66663C00L,
		0x7E06060C18181800L,
		0x3C66663C66663C00L,
		0x3C66663E060C3800L,
		0x0018180000181800L,
		0x0018180000181830L,
		0x0006186018060000L,
		0x00007E007E000000L,
		0x0060180618600000L,
		0x3C66060C18001800L,
		0x7CC6DED6DEC07800L,
		0x3C66667E66666600L,
		0x7C66667C66667C00L,
		0x1E30606060301E00L,
		0x786C6666666C7800L,
		0x7E60607860607E00L,
		0x7E60607860606000L,
		0x3C66606E66663E00L,
		0x6666667E66666600L,
		0x3C18181818183C00L,
		0x0606060606663C00L,
		0xC6CCD8F0D8CCC600L,
		0x6060606060607E00L,
		0xC6EEFED6C6C6C600L,
		0xC6E6F6DECEC6C600L,
		0x3C66666666663C00L,
		0x7C66667C60606000L,
		0x78CCCCCCCCDC7E00L,
		0x7C66667C6C666600L,
		0x3C66703C0E663C00L,
		0x7E18181818181800L,
		0x6666666666663C00L,
		0x666666663C3C1800L,
		0xC6C6C6D6FEEEC600L,
		0xC3663C183C66C300L,
		0xC3663C1818181800L,
		0xFE0C183060C0FE00L,
		0x3C30303030303C00L,
		0xC06030180C060300L,
		0x3C0C0C0C0C0C3C00L,
		0x10386CC600000000L,
		0x00000000000000FEL,
		0x18180C0000000000L,
		0x00003C063E663E00L,
		0x60607C6666667C00L,
		0x00003C6060603C00L,
		0x06063E6666663E00L,
		0x00003C667E603C00L,
		0x1C307C3030303000L,
		0x00003E66663E063CL,
		0x60607C6666666600L,
		0x1800181818180C00L,
		0x0C000C0C0C0C0C78L,
		0x6060666C786C6600L,
		0x1818181818180C00L,
		0x0000ECFED6C6C600L,
		0x00007C6666666600L,
		0x00003C6666663C00L,
		0x00007C66667C6060L,
		0x00003E66663E0606L,
		0x00007C6660606000L,
		0x00003C603C067C00L,
		0x30307C3030301C00L,
		0x0000666666663E00L,
		0x00006666663C1800L,
		0x0000C6C6D6FE6C00L,
		0x0000C66C386CC600L,
		0x00006666663C1830L,
		0x00007E0C18307E00L,
		0x0E18187018180E00L,
		0x1818181818181800L,
		0x7018180E18187000L,
		0x729C000000000000L
	};

	private static final Color SHADOW = toColor( 0x000 );
	private static final Color HIGHLIGHT = toColor( 0xFFF );
	private static final Color BACKGROUND = toColor( 0xAAA );
	private static final Color SELECTED = toColor( 0x68B );

	private static final int TEXT_SHADOW_BACKGROUND = 0;
	private static final int TEXT_HIGHLIGHT_BACKGROUND = 1;
	private static final int TEXT_SHADOW_SELECTED = 2;
	private static final int TEXT_HIGHLIGHT_SELECTED = 3;
	private static final int TEXT_BLUE = 4;
	private static final int TEXT_GREEN = 5;
	private static final int TEXT_CYAN = 6;
	private static final int TEXT_RED = 7;
	private static final int TEXT_MAGENTA = 8;
	private static final int TEXT_YELLOW = 9;
	private static final int TEXT_WHITE = 10;
	private static final int TEXT_LIME = 11;
	
	private static final int[] FX_COLOURS = new int[]
	{
		/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? @ A B C D E F */
		TEXT_GREEN, TEXT_GREEN, TEXT_GREEN, TEXT_GREEN, TEXT_GREEN, TEXT_LIME,
		TEXT_LIME, TEXT_YELLOW, TEXT_YELLOW, TEXT_MAGENTA, 0, 0, 0, 0, 0, 0, 0,
		TEXT_YELLOW, TEXT_WHITE, TEXT_YELLOW, TEXT_WHITE, TEXT_BLUE, TEXT_WHITE,
		/* G H I J K L M N O P Q R S T U V W X Y Z [ \ ] ^ _ ` */
		TEXT_YELLOW, TEXT_YELLOW, TEXT_BLUE, TEXT_BLUE, TEXT_BLUE, TEXT_MAGENTA,
		TEXT_BLUE, TEXT_BLUE, TEXT_BLUE, TEXT_YELLOW, TEXT_BLUE, TEXT_MAGENTA,
		TEXT_BLUE, TEXT_YELLOW, TEXT_BLUE, TEXT_BLUE, TEXT_BLUE, TEXT_GREEN,
		TEXT_BLUE, TEXT_BLUE, 0, 0, 0, 0, 0, 0,
		/* a b c d e f g h i j k l m n o p q r s t u v w x y z { | } ~ */
		TEXT_WHITE, TEXT_WHITE, TEXT_WHITE, TEXT_YELLOW, TEXT_GREEN, TEXT_GREEN,
		TEXT_GREEN, TEXT_GREEN, TEXT_YELLOW, TEXT_GREEN, TEXT_LIME, TEXT_LIME,
		TEXT_BLUE, TEXT_BLUE, TEXT_MAGENTA, TEXT_BLUE, TEXT_MAGENTA, TEXT_YELLOW,
		TEXT_BLUE, TEXT_WHITE, TEXT_GREEN, TEXT_YELLOW, TEXT_BLUE, TEXT_BLUE,
		TEXT_BLUE, TEXT_BLUE, 0, 0, 0, 0
	};
	
	private static final int[] EX_COLOURS = new int[]
	{
		/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? @ A B C D E F */
		TEXT_BLUE, TEXT_GREEN, TEXT_GREEN, TEXT_GREEN, TEXT_GREEN, TEXT_GREEN,
		TEXT_WHITE, TEXT_YELLOW, TEXT_BLUE, TEXT_MAGENTA, 0, 0, 0, 0, 0, 0, 0,
		TEXT_YELLOW, TEXT_YELLOW, TEXT_MAGENTA, TEXT_MAGENTA, TEXT_MAGENTA, TEXT_MAGENTA
	};
	
	private static final int[] SX_COLOURS = new int[]
	{
		/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? @ A B C D E F */
		TEXT_YELLOW, TEXT_MAGENTA, TEXT_GREEN, TEXT_GREEN, TEXT_YELLOW, TEXT_BLUE,
		TEXT_BLUE, TEXT_BLUE, TEXT_YELLOW, TEXT_BLUE, 0, 0, 0, 0, 0, 0, 0,
		TEXT_YELLOW, TEXT_MAGENTA, TEXT_MAGENTA, TEXT_MAGENTA, TEXT_MAGENTA, TEXT_MAGENTA
	};
	
	private static final int[] VC_COLOURS = new int[]
	{
		/* 0 1 2 3 4 5 6 7 8 9 : ; < = > ? @ A B C D E F */
		TEXT_YELLOW, TEXT_YELLOW, TEXT_YELLOW, TEXT_YELLOW, TEXT_YELLOW, TEXT_YELLOW,
		TEXT_YELLOW, TEXT_YELLOW, TEXT_YELLOW, TEXT_YELLOW, 0, 0, 0, 0, 0, 0, 0,
		TEXT_GREEN, TEXT_GREEN, TEXT_YELLOW, TEXT_YELLOW, TEXT_YELLOW, TEXT_GREEN
	};
	
	private static final int[] KEY_MAP = new int[]
	{
		KeyEvent.VK_Z, KeyEvent.VK_S, KeyEvent.VK_X, KeyEvent.VK_D,
		KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_G, KeyEvent.VK_B,
		KeyEvent.VK_H, KeyEvent.VK_N, KeyEvent.VK_J, KeyEvent.VK_M,
		KeyEvent.VK_Q, KeyEvent.VK_2, KeyEvent.VK_W, KeyEvent.VK_3,
		KeyEvent.VK_E, KeyEvent.VK_R, KeyEvent.VK_5, KeyEvent.VK_T,
		KeyEvent.VK_6, KeyEvent.VK_Y, KeyEvent.VK_7, KeyEvent.VK_U,
		KeyEvent.VK_I, KeyEvent.VK_9, KeyEvent.VK_O, KeyEvent.VK_0,
		KeyEvent.VK_P
	};
	
	private static final int[] HEX_MAP = new int[]
	{
		KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3,
		KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7,
		KeyEvent.VK_8, KeyEvent.VK_9, KeyEvent.VK_A, KeyEvent.VK_B,
		KeyEvent.VK_C, KeyEvent.VK_D, KeyEvent.VK_E, KeyEvent.VK_F
	};
	
	private static final String KEY_TO_STR = "A-A#B-C-C#D-D#E-F-F#G-G#";
	private static final String HEX_TO_STR = "0123456789ABCDEF";
	
	private static final int GAD_COUNT = 32;
	private static final int GAD_TYPE_LABEL = 1;
	private static final int GAD_TYPE_BUTTON = 2;
	private static final int GAD_TYPE_TEXTBOX = 3;
	private static final int GAD_TYPE_VSLIDER = 4;
	private static final int GAD_TYPE_HSLIDER = 5;
	private static final int GAD_TYPE_LISTBOX = 6;
	private static final int GAD_TYPE_PATTERN = 7;
	
	private static final int KEY_ESCAPE = KeyEvent.VK_ESCAPE;
	private static final int KEY_BACKSPACE = KeyEvent.VK_BACK_SPACE;
	private static final int KEY_DELETE = KeyEvent.VK_DELETE;
	private static final int KEY_HOME = KeyEvent.VK_HOME;
	private static final int KEY_END = KeyEvent.VK_END;
	private static final int KEY_PAGE_UP = KeyEvent.VK_PAGE_UP;
	private static final int KEY_PAGE_DOWN = KeyEvent.VK_PAGE_DOWN;
	private static final int KEY_UP = KeyEvent.VK_UP;
	private static final int KEY_DOWN = KeyEvent.VK_DOWN;
	private static final int KEY_LEFT = KeyEvent.VK_LEFT;
	private static final int KEY_RIGHT = KeyEvent.VK_RIGHT;
	
	private static final int GADNUM_PATTERN = 1;
	private static final int GADNUM_PATTERN_VSLIDER = 2;
	private static final int GADNUM_PATTERN_HSLIDER = 3;
	private static final int GADNUM_DIR_TEXTBOX = 4;
	private static final int GADNUM_DIR_BUTTON = 5;
	private static final int GADNUM_DIR_LISTBOX = 6;
	private static final int GADNUM_DIR_SLIDER = 7;
	private static final int GADNUM_TITLE_LABEL = 8;
	private static final int GADNUM_TITLE_TEXTBOX = 9;
	private static final int GADNUM_INST_LABEL = 10;
	private static final int GADNUM_INST_TEXTBOX = 11;
	private static final int GADNUM_INST_DEC_BUTTON = 12;
	private static final int GADNUM_INST_INC_BUTTON = 13;
	private static final int GADNUM_INST_LISTBOX = 14;
	private static final int GADNUM_INST_SLIDER = 15;
	private static final int GADNUM_SEQ_LISTBOX = 16;
	private static final int GADNUM_SEQ_SLIDER = 17;
	private static final int GADNUM_LOAD_BUTTON = 18;
	private static final int GADNUM_VER_LABEL = 19;
	private static final int GADNUM_PLAY_BUTTON = 20;
	
	private static final int SAMPLING_RATE = 48000;
	
	private int displayChannels, width, height, clickX, clickY, focus;
	
	private int[] gadType = new int[ GAD_COUNT ];
	private int[] gadX = new int[ GAD_COUNT ];
	private int[] gadY = new int[ GAD_COUNT ];
	private int[] gadWidth = new int[ GAD_COUNT ];
	private int[] gadHeight = new int[ GAD_COUNT ];
	private boolean[] gadRedraw = new boolean[ GAD_COUNT ];
	private String[][] gadText = new String[ GAD_COUNT ][];
	private int[][] gadValues = new int[ GAD_COUNT ][];
	private boolean[] gadSelected = new boolean[ GAD_COUNT ];
	private int[] gadValue = new int[ GAD_COUNT ];
	private int[] gadRange = new int[ GAD_COUNT ];
	private int[] gadMax = new int[ GAD_COUNT ];
	private int[] gadItem = new int[ GAD_COUNT ];
	private int[] gadLink = new int[ GAD_COUNT ];
	
	private Image charset, image;
	
	private Module module = new Module();
	private IBXM ibxm = new IBXM( module, SAMPLING_RATE );
	private int instrument, octave = 4, selectedFile, triggerChannel;
	private int[] keyChannel = new int[ 97 ];
	private boolean reverb;
	private String error;
	private long mute;
	
	private static int reverb( int[] buf, int[] reverbBuf, int reverbIdx, int count )
	{
		for( int idx = 0; idx < count; idx++ )
		{
			buf[ idx * 2 ] = ( buf[ idx * 2 ] * 3 + reverbBuf[ reverbIdx + 1 ] ) >> 2;
			buf[ idx * 2 + 1 ] = ( buf[ idx * 2 + 1 ] * 3 + reverbBuf[ reverbIdx ] ) >> 2;
			reverbBuf[ reverbIdx ] = buf[ idx * 2 ];
			reverbBuf[ reverbIdx + 1 ] = buf[ idx * 2 + 1 ];
			reverbIdx += 2;
			if( reverbIdx >= reverbBuf.length )
			{
				reverbIdx = 0;
			}
		}
		return reverbIdx;
	}
	
	private static void clip( int[] inputBuf, byte[] outputBuf, int count )
	{
		for( int idx = 0; idx < count; idx++ )
		{
			int ampl = inputBuf[ idx ];
			if( ampl > 32767 )
			{
				ampl = 32767;
			}
			if( ampl < -32768 )
			{
				ampl = -32768;
			}
			outputBuf[ idx * 2 ] = ( byte ) ampl;
			outputBuf[ idx * 2 + 1 ] = ( byte ) ( ampl >> 8 );
		}
	}
	
	private static String pad( String string, char chr, int length, boolean left )
	{
		if( string.length() < length )
		{
			char[] chars = new char[ length ];
			for( int idx = 0; idx < chars.length; idx++ )
			{
				chars[ idx ] = chr;
			}
			string.getChars( 0, string.length(), chars, left ? length - string.length() : 0 );
			return new String( chars );
		}
		return string.substring( 0, length );
	}
	
	private static String[] split( String input, char sep )
	{
		int count = 0;
		for( int idx = 0, len = input.length(); idx < len; idx++ )
		{
			if( input.charAt( idx ) == sep )
			{
				count++;
			}
		}
		String[] output = new String[ count + 1 ];
		int offset = 0;
		count = 0;
		for( int idx = 0, len = input.length(); idx < len; idx++ )
		{
			if( input.charAt( idx ) == sep )
			{
				output[ count++ ] = input.substring( offset, idx );
				offset = idx + 1;
			}
		}
		output[ count ] = input.substring( offset, input.length() );
		return output;
	}
	
	private static Color toColor( int rgb12 )
	{
		return new Color( ( ( rgb12 >> 8 ) & 0xF ) * 17, ( ( rgb12 >> 4 ) & 0xF ) * 17, ( rgb12 & 0xF ) * 17 );
	}
	
	private static int toRgb12( Color clr )
	{
		return ( clr.getRed() / 17 << 8 ) | ( clr.getGreen() / 17 << 4 ) | ( clr.getBlue() / 17 );
	}
	
	private static int toRgb24( int rgb12 )
	{
		int r = ( ( rgb12 & 0xF00 ) >> 8 ) * 17;
		int g = ( ( rgb12 & 0xF0 ) >> 4 ) * 17;
		int b = ( rgb12 & 0xF ) * 17;
		return ( r << 16 ) | ( g << 8 ) | b;
	}
	
	private void createDiskGadgets( int x, int y, int rows, int cols )
	{
		createTextbox( GADNUM_DIR_TEXTBOX, x, y, ( cols - 1 ) * 8, 28, "" );
		createButton( GADNUM_DIR_BUTTON, x + ( cols - 1 ) * 8 + 4, y + 2, 44, 24, "Dir" );
		createListbox( GADNUM_DIR_LISTBOX, x, y + 32, ( cols + 2 ) * 8, rows * 16 + 12, GADNUM_DIR_SLIDER );
		createVSlider( GADNUM_DIR_SLIDER, x + ( cols + 2 ) * 8 + 4, y + 32, 20, rows * 16 + 12, 1, 1 );
		createButton( GADNUM_LOAD_BUTTON, x, y + rows * 16 + 48, 96, 24, "Load" );
	}
	
	private void createInstGadgets( int x, int y, int rows, int cols )
	{
		createLabel( GADNUM_INST_LABEL, x, y + 6, "Instrument", TEXT_SHADOW_SELECTED );
		createTextbox( GADNUM_INST_TEXTBOX, x + 10 * 8 + 4, y, 4 * 8, 28, "00" );
		createButton( GADNUM_INST_DEC_BUTTON, x + 15 * 8, y + 2, 24, 24, "<" );
		createButton( GADNUM_INST_INC_BUTTON, x + 15 * 8 + 28, y + 2, 24, 24, ">" );
		createListbox( GADNUM_INST_LISTBOX, x, y + 32, ( cols + 2 ) * 8, rows * 16 + 12, GADNUM_INST_SLIDER );
		createVSlider( GADNUM_INST_SLIDER, x + ( cols + 2 ) * 8 + 4, y + 32, 20, rows * 16 + 12, 1, 1 );
	}
	
	private void createSequenceGadgets( int x, int y, int rows )
	{
		createListbox( GADNUM_SEQ_LISTBOX, x, y, 9 * 8, rows * 16 + 12, GADNUM_SEQ_SLIDER );
		gadText[ GADNUM_SEQ_LISTBOX ] = new String[] { "000   0" };
		createVSlider( GADNUM_SEQ_SLIDER, x + 9 * 8 + 4, y, 20, rows * 16 + 12, 1, 1 );
		createButton( GADNUM_PLAY_BUTTON, x, y + rows * 16 + 16, 96, 24, "Play" );
	}
	
	public IBXMPlayer3( int channels )
	{
		int rows = 9;
		displayChannels = channels;
		addKeyListener( this );
		addMouseListener( this );
		addMouseMotionListener( this );
		createPattern( GADNUM_PATTERN, 4, 80 + rows * 16, channels, GADNUM_PATTERN_VSLIDER );
		createVSlider( GADNUM_PATTERN_VSLIDER, gadWidth[ GADNUM_PATTERN ] + 8, 80 + rows * 16, 20, 256, 15, 78 );
		createHSlider( GADNUM_PATTERN_HSLIDER, 4, 340 + rows * 16, gadWidth[ GADNUM_PATTERN ], 20, channels, channels );
		gadLink[ GADNUM_PATTERN_HSLIDER ] = GADNUM_PATTERN;
		createDiskGadgets( 4, 4, rows, ( channels - 8 ) * 11 + 36 );
		createLabel( GADNUM_TITLE_LABEL, ( channels * 11 - 21 ) * 8, 4 + 6, "Title", TEXT_SHADOW_SELECTED );
		createTextbox( GADNUM_TITLE_TEXTBOX, ( channels * 11 - 16 ) * 8 + 4, 4, 23 * 8, 28, module.songName );
		createInstGadgets( ( channels * 11 - 46 ) * 8, 4, rows, 36 );
		createSequenceGadgets( ( channels * 11 - 5 ) * 8 + 4, 36, rows );
		String version = "IBXM " + IBXM.VERSION;
		int x = 4 + ( ( channels * 11 + 4 ) * 8 - version.length() * 8 ) / 2;
		createLabel( GADNUM_VER_LABEL, x, 6 + rows * 16 + 50, version, TEXT_HIGHLIGHT_SELECTED );
		ibxm.setSequencerEnabled( false );
		setInstrument( 1 );
		listDir( getDir() );
		gadRedraw[ 0 ] = true;
		width = gadWidth[ GADNUM_PATTERN ] + 32;
		height = gadY[ GADNUM_PATTERN ] + gadHeight[ GADNUM_PATTERN ] + 28;
	}
	
	public synchronized void keyPressed( KeyEvent e )
	{
		try
		{
			switch( e.getKeyCode() )
			{
				case KeyEvent.VK_F1:
					octave = 3;
					break;
				case KeyEvent.VK_F2:
					octave = 4;
					break;
				case KeyEvent.VK_F3:
					octave = 5;
					break;
				case KeyEvent.VK_F4:
					octave = 6;
					break;
				case KeyEvent.VK_F5:
					reverb = !reverb;
					break;
				case KeyEvent.VK_F6:
					ibxm.setInterpolation( Channel.NEAREST );
					break;
				case KeyEvent.VK_F7:
					ibxm.setInterpolation( Channel.LINEAR );
					break;
				case KeyEvent.VK_F8:
					ibxm.setInterpolation( Channel.SINC );
					break;
				default:
					switch( gadType[ focus ] )
					{
						case GAD_TYPE_TEXTBOX:
							keyTextbox( focus, e.getKeyChar(), e.getKeyCode(), e.isShiftDown() );
							break;
						case GAD_TYPE_LISTBOX:
							keyListbox( focus, e.getKeyChar(), e.getKeyCode(), e.isShiftDown() );
							trigger( -1, mapNoteKey( e ), e.isShiftDown() );
							break;
						default:
							trigger( -1, mapNoteKey( e ), e.isShiftDown() );
							break;
					}
					break;
			}
		}
		catch( Exception x )
		{
			x.printStackTrace();
			setError( x.getMessage() );
		}
		repaint();
	}
	
	public synchronized void keyReleased( KeyEvent e )
	{
		switch( gadType[ focus ] )
		{
			case GAD_TYPE_TEXTBOX:
				break;
			default:
				release( mapNoteKey( e ) );
				break;
		}
	}
	
	public synchronized void keyTyped( KeyEvent e )
	{
	}
	
	public synchronized void mouseClicked( MouseEvent e )
	{
	}
	
	public synchronized void mouseEntered( MouseEvent e )
	{
	}
	
	public synchronized void mouseExited( MouseEvent e )
	{
	}
	
	public synchronized void mousePressed( MouseEvent e )
	{
		clickX = e.getX();
		clickY = e.getY();
		int clicked = findGadget( clickX, clickY );
		if( focus > 0 && focus != clicked )
		{
			escape( focus );
			gadRedraw[ focus ] = true;
		}
		switch( gadType[ clicked ] )
		{
			case GAD_TYPE_BUTTON:
				gadSelected[ clicked ] = true;
				gadRedraw[ clicked ] = true;
				break;
			case GAD_TYPE_TEXTBOX:
				clickTextbox( clicked );
				break;
			case GAD_TYPE_VSLIDER:
				clickVSlider( clicked, e.isShiftDown() );
				break;
			case GAD_TYPE_HSLIDER:
				clickHSlider( clicked, e.isShiftDown() );
				break;
			case GAD_TYPE_LISTBOX:
				clickListbox( clicked, e.isShiftDown() );
				break;
			case GAD_TYPE_PATTERN:
				clickPattern( clicked, e.isShiftDown() );
				break;
			default:
				if( clicked > 0 )
				{
					action( clicked, e.isShiftDown() );
				}
		}
		focus = clicked;
		repaint();
	}
	
	public synchronized void mouseReleased( MouseEvent e )
	{
		if( focus > 0 )
		{
			switch( gadType[ focus ] )
			{
				case GAD_TYPE_BUTTON:
					if( findGadget( e.getX(), e.getY() ) == focus )
					{
						gadSelected[ focus ] = false;
						gadRedraw[ focus ] = true;
						action( focus, e.isShiftDown() );
						focus = 0;
						repaint();
					}
					break;
				case GAD_TYPE_VSLIDER:
				case GAD_TYPE_HSLIDER:
					action( focus, e.isShiftDown() );
					focus = 0;
					repaint();
					break;
			}
		}
	}
	
	public synchronized void mouseDragged( MouseEvent e )
	{
		if( focus > 0 )
		{
			switch( gadType[ focus ] )
			{
				case GAD_TYPE_BUTTON:
					boolean selected = findGadget( e.getX(), e.getY() ) == focus;
					if( gadSelected[ focus ] != selected )
					{
						gadSelected[ focus ] = selected;
						gadRedraw[ focus ] = true;
						repaint();
					}
					break;
				case GAD_TYPE_VSLIDER:
					dragVSlider( focus, e.getY() );
					break;
				case GAD_TYPE_HSLIDER:
					dragHSlider( focus, e.getX() );
					break;
			}
		}
	}
	
	public synchronized void mouseMoved( MouseEvent e )
	{
	}
	
	public synchronized void windowActivated( WindowEvent e )
	{
	}
	
	public synchronized void windowClosed( WindowEvent e )
	{
	}
	
	public synchronized void windowClosing( WindowEvent e )
	{
		e.getWindow().dispose();
	}
	
	public synchronized void windowDeactivated( WindowEvent e )
	{
	}
	
	public synchronized void windowDeiconified( WindowEvent e )
	{
	}
	
	public synchronized void windowIconified( WindowEvent e )
	{
	}
	
	public synchronized void windowOpened( WindowEvent e )
	{
	}
	
	public synchronized void paint( Graphics g ) {
		if( charset == null ) {
			charset = createImage( charsetImage( TOPAZ_8 ).getSource() );
		}
		if( image == null ) {
			image = createImage( width, height );
		}
		boolean redraw = gadRedraw[ 0 ];
		for( int idx = 1; idx < GAD_COUNT && !redraw; idx++ )
		{
			redraw = gadRedraw[ idx ];
		}
		if( redraw )
		{
			Graphics imageGraphics = image.getGraphics();
			try
			{
				if( gadRedraw[ 0 ] )
				{
					imageGraphics.setColor( SELECTED );
					imageGraphics.fillRect( 0, 0, width, height );
				}
				for( int idx = 1; idx < GAD_COUNT; idx++ )
				{
					if( gadRedraw[ idx ] || gadRedraw[ 0 ] )
					{
						switch( gadType[ idx ] )
						{
							case GAD_TYPE_LABEL:
								drawLabel( imageGraphics, idx );
								break;
							case GAD_TYPE_BUTTON:
								drawButton( imageGraphics, idx );
								break;
							case GAD_TYPE_TEXTBOX:
								drawTextbox( imageGraphics, idx );
								break;
							case GAD_TYPE_VSLIDER:
								if( gadLink[ gadLink[ idx ] ] != idx )
								{
									drawVSlider( imageGraphics, idx );
								}
								break;
							case GAD_TYPE_HSLIDER:
								if( gadLink[ gadLink[ idx ] ] != idx )
								{
									drawHSlider( imageGraphics, idx );
								}
								break;
							case GAD_TYPE_LISTBOX:
								drawListbox( imageGraphics, idx );
								break;
							case GAD_TYPE_PATTERN:
								drawPattern( imageGraphics, idx );
								break;
						}
						gadRedraw[ idx ] = false;
					}
				}
				gadRedraw[ 0 ] = false;
			}
			finally
			{
				imageGraphics.dispose();
			}
		}
		g.drawImage( image, 0, 0, null );
	}
	
	public synchronized void update( Graphics g ) {
		paint( g );
	}
	
	public synchronized Dimension getPreferredSize()
	{
		return new Dimension( width, height );
	}
	
	private static int readIntBe( java.io.InputStream inputStream, int length ) throws IOException
	{
		int value = 0;
		for( int idx = 0; idx < length; idx++ )
		{
			value = ( value << 8 ) | inputStream.read();
		}
		return value;
	}
	
	private static int readIntLe( java.io.InputStream inputStream, int length ) throws IOException
	{
		int value = 0;
		for( int idx = 0; idx < length; idx++ )
		{
			value = value | ( inputStream.read() << idx * 8 );
		}
		return value;
	}
	
	private static void drawChar( long[] source, int chr, int x, int y, int bg, int fg, int[] dest, int stride )
	{
		int destIdx = y * stride + x;
		for( int cy = 0; cy < 8; cy++ ) {
			for( int cx = 0; cx < 8; cx++ ) {
				int pixel = ( ( source[ chr ] >> 63 - cy * 8 - cx ) & 1 ) == 0 ? bg : fg;
				dest[ destIdx + cx ] = dest[ destIdx + cx + stride ] = pixel;
			}
			destIdx += stride * 2;
		}
	}
	
	private static Image iconImage()
	{
		BufferedImage image = new BufferedImage( 32, 32, BufferedImage.TYPE_INT_RGB );
		int[] pixels = new int[ 32 * 32 ];
		drawChar( TOPAZ_8, 'I' - 32, 0, 9, 0, toRgb24( 0x07F ), pixels, 32 );
		drawChar( TOPAZ_8, 'B' - 32, 8, 9, 0, toRgb24( 0x07F ), pixels, 32 );
		drawChar( TOPAZ_8, 'X' - 32, 16, 9, 0, toRgb24( 0xF70 ), pixels, 32 );
		drawChar( TOPAZ_8, 'M' - 32, 24, 9, 0, toRgb24( 0xF70 ), pixels, 32 );
		image.setRGB( 0, 0, 32, 32, pixels, 0, 32 );
		return image;
	}
	
	private static Image charsetImage( long[] source )
	{
		int[] pal = new int[]
		{
			( toRgb12( BACKGROUND ) << 12 ) | toRgb12( SHADOW ),
			( toRgb12( BACKGROUND ) << 12 ) | toRgb12( HIGHLIGHT ),
			( toRgb12( SELECTED ) << 12 ) | toRgb12( SHADOW ),
			( toRgb12( SELECTED ) << 12 ) | toRgb12( HIGHLIGHT ),
		/*  Blue   Green  Cyan   Red   Magenta Yellow White  Lime */
			0x00C, 0x080, 0x088, 0x800, 0x808, 0x860, 0x888, 0x680,
			0x06F, 0x0F0, 0x0FF, 0xF00, 0xF0F, 0xFC0, 0xFFF, 0xCF0
		};
		int w = 8 * source.length;
		int h = 16 * pal.length;
		int[] pixels = new int[ w * h ];
		for( int clr = 0; clr < pal.length; clr++ ) {
			int bg = toRgb24( pal[ clr ] >> 12 );
			int fg = toRgb24( pal[ clr ] & 0xFFF );
			for( int chr = 0; chr < source.length; chr++ ) {
				drawChar( source, chr, chr * 8, clr * 16, bg, fg, pixels, w );
			}
		}
		BufferedImage image = new BufferedImage( w, h, BufferedImage.TYPE_INT_RGB );
		image.setRGB( 0, 0, w, h, pixels, 0, w );
		return image;
	}
	
	private void setError( String message )
	{
		error = message;
		gadRedraw[ GADNUM_PATTERN ] = true;
	}
	
	private void drawText( Graphics g, int x, int y, String text, int colour )
	{
		for( int idx = 0, len = text.length(); idx < len; idx++ )
		{
			int chr = text.charAt( idx );
			if( chr < 32 || ( chr > 126 && chr < 192 ) || chr > 255 )
			{
				chr = 32;
			}
			else if( chr >= 192 )
			{
				chr = "AAAAAAECEEEEIIIIDNOOOOO*0UUUUYPSaaaaaaeceeeeiiiidnooooo/0uuuuypy".charAt( chr - 192 );
			}
			g.setClip( x, y, 8, 16 );
			g.drawImage( charset, x - ( chr - 32 ) * 8, y - colour * 16, null );
			x += 8;
		}
		g.setClip( null );
	}
	
	private void drawInt( Graphics g, int x, int y, int value, int len, int colour )
	{
		char[] chars = new char[ len ];
		while( len > 0 ) {
			len = len - 1;
			chars[ len ] = ( char ) ( '0' + value % 10 );
			value = value / 10;
		}
		drawText( g, x, y, new String( chars ), colour );
	}
	
	private void raiseBox( Graphics g, int x, int y, int w, int h )
	{
		g.setColor( SHADOW );
		g.fillRect( x + w - 2, y, 2, h );
		g.setColor( HIGHLIGHT );
		g.fillRect( x, y, 2, h );
		g.setColor( SHADOW );
		g.fillRect( x + 1, y + h - 2, w - 1, 2 );
		g.setColor( HIGHLIGHT );
		g.fillRect( x, y, w - 1, 2 );
	}

	private void lowerBox( Graphics g, int x, int y, int w, int h )
	{
		g.setColor( HIGHLIGHT );
		g.fillRect( x + w - 2, y, 2, h );
		g.setColor( SHADOW );
		g.fillRect( x, y, 2, h );
		g.setColor( HIGHLIGHT );
		g.fillRect( x + 1, y + h - 2, w - 1, 2 );
		g.setColor( SHADOW );
		g.fillRect( x, y, w - 1, 2 );
	}

	private void bevelBox( Graphics g, int x, int y, int w, int h )
	{
		raiseBox( g, x, y, w, h );
		lowerBox( g, x + 2, y + 2, w - 4, h - 4 );
	}
	
	private void drawLabel( Graphics g, int gadnum )
	{
		drawText( g, gadX[ gadnum ], gadY[ gadnum ], gadText[ gadnum ][ 0 ], gadValue[ gadnum ] );
	}
	
	private void drawButton( Graphics g, int gadnum )
	{
		int x = gadX[ gadnum ];
		int y = gadY[ gadnum ];
		int w = gadWidth[ gadnum ];
		int h = gadHeight[ gadnum ];
		int textColour;
		if( gadSelected[ gadnum ] )
		{
			g.setColor( SELECTED );
			g.fillRect( x, y, w, h );
			lowerBox( g, x, y, w, h );
			textColour = TEXT_HIGHLIGHT_SELECTED;
		}
		else
		{
			g.setColor( BACKGROUND );
			g.fillRect( x, y, w, h );
			raiseBox( g, x, y, w, h );
			textColour = TEXT_SHADOW_BACKGROUND;
		}
		String text = gadText[ gadnum ][ 0 ];
		drawText( g, x + ( w - text.length() * 8 ) / 2,
			y + ( h - 14 ) / 2, text, textColour );
	}
	
	private void drawTextbox( Graphics g, int gadnum )
	{
		int x = gadX[ gadnum ];
		int y = gadY[ gadnum ];
		int w = gadWidth[ gadnum ];
		int h = gadHeight[ gadnum ];
		String text = gadText[ gadnum ][ 0 ];
		int cursor = gadItem[ gadnum ];
		int columns = ( w - 16 ) / 8;
		int offset = focus == gadnum ? gadValue[ gadnum ] : text.length() - columns;
		if( offset < 0 || offset > text.length() )
		{
			offset = 0;
		}
		if( offset + columns > text.length() )
		{
			columns = text.length() - offset;
		}
		g.setColor( BACKGROUND );
		g.fillRect( x, y, w, h );
		drawText( g, x + 8, y + 6, text.substring( offset, offset + columns ), TEXT_SHADOW_BACKGROUND );
		if( focus == gadnum && cursor >= offset && cursor <= offset + columns )
		{
			String chr = cursor < offset + columns ? String.valueOf( text.charAt( cursor ) ) : " ";
			drawText( g, x + ( cursor - offset + 1 ) * 8, y + 6, chr, TEXT_SHADOW_SELECTED );
		}
		bevelBox( g, x, y, w, h );
	}
	
	private void clickTextbox( int gadnum )
	{
		int columns = ( gadWidth[ gadnum ] - 16 ) / 8;
		String text = gadText[ gadnum ][ 0 ];
		int offset = focus == gadnum ? gadValue[ gadnum ] : text.length() - columns;
		if( offset < 0 || offset > text.length() )
		{
			offset = 0;
		}
		if( offset + columns > text.length() )
		{
			columns = text.length() - offset;
		}
		int cursor = offset + ( clickX - gadX[ gadnum ] ) / 8 - 1;
		if( cursor > text.length() )
		{
			cursor = text.length();
		}
		if( cursor < 0 )
		{
			cursor = 0;
		}
		gadValue[ gadnum ] = offset;
		gadItem[ gadnum ] = cursor;
		gadRedraw[ gadnum ] = true;
	}

	private void keyTextbox( int gadnum, char chr, int key, boolean shift )
	{
		int columns = ( gadWidth[ gadnum ] - 16 ) / 8;
		String text = gadText[ gadnum ][ 0 ];
		int offset = gadValue[ gadnum ];
		if( offset < 0 || offset > text.length() )
		{
			offset = 0;
		}
		int cursor = gadItem[ gadnum ];
		if( cursor > text.length() )
		{
			cursor = text.length();
		}
		switch( key ) 
		{
			case KEY_BACKSPACE:
				if( cursor > 0 )
				{
					text = text.substring( 0, cursor - 1 ) + text.substring( cursor );
					cursor--;
					if( cursor < offset )
					{
						offset = cursor;
					}
				}
				break;
			case KEY_DELETE:
				if( cursor < text.length() )
				{
					text = text.substring( 0, cursor ) + text.substring( cursor + 1 );
				}
				break;
			case KEY_END:
				cursor = text.length();
				if( cursor - offset >= columns )
				{
					offset = cursor - columns;
				}
				break;
			case KEY_ESCAPE:
				escape( gadnum );
				text = gadText[ gadnum ][ 0 ];
				focus = 0;
				break;
			case KEY_HOME:
				offset = cursor = 0;
				break;
			case KEY_LEFT:
				if( cursor > 0 )
				{
					cursor--;
					if( cursor < offset )
					{
						offset = cursor;
					}
				}
				break;
			case KEY_RIGHT:
				if( cursor < text.length() )
				{
					cursor++;
					if( cursor - offset >= columns )
					{
						offset = cursor - columns + 1;
					}
				}
				break;
			default:
				if( chr == 10 )
				{
					action( gadnum, shift );
					text = gadText[ gadnum ][ 0 ];
					focus = 0;
				}
				else if( chr >= 32 && chr < 127 )
				{
					text = text.substring( 0, cursor )
						+ String.valueOf( chr ) + text.substring( cursor );
					cursor++;
					if( cursor - offset > columns )
					{
						offset = cursor - columns;
					}
				}
				break;
		}
		gadText[ gadnum ][ 0 ] = text;
		gadValue[ gadnum ] = offset;
		gadItem[ gadnum ] = cursor;
		gadRedraw[ gadnum ] = true;
	}
	
	private void drawVSlider( Graphics g, int gadnum )
	{
		int x = gadX[ gadnum ];
		int y = gadY[ gadnum ];
		int w = gadWidth[ gadnum ];
		int h = gadHeight[ gadnum ];
		int s = ( h - 12 ) * gadRange[ gadnum ] / gadMax[ gadnum ] + 8;
		int d = ( h - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ];
		g.setColor( SELECTED );
		g.fillRect( x, y, w, h );
		lowerBox( g, x, y, w, h );
		g.setColor( BACKGROUND );
		g.fillRect( x + 2, y + d + 2, w - 4, s );
		raiseBox( g, x + 2, y + d + 2, w - 4, s );
	}
	
	private void drawHSlider( Graphics g, int gadnum )
	{
		int x = gadX[ gadnum ];
		int y = gadY[ gadnum ];
		int w = gadWidth[ gadnum ];
		int h = gadHeight[ gadnum ];
		int s = ( w - 12 ) * gadRange[ gadnum ] / gadMax[ gadnum ] + 8;
		int d = ( w - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ];
		g.setColor( SELECTED );
		g.fillRect( x, y, w, h );
		lowerBox( g, x, y, w, h );
		g.setColor( BACKGROUND );
		g.fillRect( x + d + 2, y + 2, s, h - 4 );
		raiseBox( g, x + d + 2, y + 2, s, h - 4 );
	}
	
	private void clickVSlider( int gadnum, boolean shift )
	{
		int ss = ( gadHeight[ gadnum ] - 12 ) * gadRange[ gadnum ] / gadMax[ gadnum ] + 8;
		int so = ( gadHeight[ gadnum ] - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ];
		int sy = gadY[ gadnum ] + so + 2;
		if( clickY < sy )
		{
			int sp = gadValue[ gadnum ] - gadRange[ gadnum ];
			if( sp < 0 )
			{
				sp = 0;
			}
			gadValue[ gadnum ] = sp;
			if( gadLink[ gadnum ] > 0 )
			{
				gadRedraw[ gadLink[ gadnum ] ] = true;
			}
			if( gadLink[ gadLink[ gadnum ] ] != gadnum )
			{
				gadRedraw[ gadnum ] = true;
			}
			action( gadnum, shift );
		}
		else if( clickY > ( sy + ss ) )
		{
			int sp = gadValue[ gadnum ] + gadRange[ gadnum ];
			if( sp > ( gadMax[ gadnum ] - gadRange[ gadnum ] ) )
			{
				sp = gadMax[ gadnum ] - gadRange[ gadnum ];
			}
			gadValue[ gadnum ] = sp;
			if( gadLink[ gadnum ] > 0 )
			{
				gadRedraw[ gadLink[ gadnum ] ] = true;
			}
			if( gadLink[ gadLink[ gadnum ] ] != gadnum )
			{
				gadRedraw[ gadnum ] = true;
			}
			action( gadnum, shift );
		}
	}
	
	private void clickHSlider( int gadnum, boolean shift )
	{
		int ss = ( gadWidth[ gadnum ] - 12 ) * gadRange[ gadnum ] / gadMax[ gadnum ] + 8;
		int so = ( gadWidth[ gadnum ] - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ];
		int sx = gadX[ gadnum ] + so + 2;
		if( clickX < sx )
		{
			int sp = gadValue[ gadnum ] - gadRange[ gadnum ];
			if( sp < 0 )
			{
				sp = 0;
			}
			gadValue[ gadnum ] = sp;
			if( gadLink[ gadnum ] > 0 )
			{
				gadRedraw[ gadLink[ gadnum ] ] = true;
			}
			if( gadLink[ gadLink[ gadnum ] ] != gadnum )
			{
				gadRedraw[ gadnum ] = true;
			}
			action( gadnum, shift );
		}
		else if( clickX > ( sx + ss ) )
		{
			int sp = gadValue[ gadnum ] + gadRange[ gadnum ];
			if( sp > ( gadMax[ gadnum ] - gadRange[ gadnum ] ) )
			{
				sp = gadMax[ gadnum ] - gadRange[ gadnum ];
			}
			gadValue[ gadnum ] = sp;
			if( gadLink[ gadnum ] > 0 )
			{
				gadRedraw[ gadLink[ gadnum ] ] = true;
			}
			if( gadLink[ gadLink[ gadnum ] ] != gadnum )
			{
				gadRedraw[ gadnum ] = true;
			}
			action( gadnum, shift );
		}
	}
	
	private void dragVSlider( int gadnum, int y )
	{
		int ss = ( gadHeight[ gadnum ] - 12 ) * gadRange[ gadnum ] / gadMax[ gadnum ] + 8;
		int so = ( gadHeight[ gadnum ] - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ];
		int sg = gadHeight[ gadnum ] - 4 - ss;
		int sy = gadY[ gadnum ] + so + 2;
		if( clickY > sy && clickY < ( sy + ss ) )
		{
			int sp = so + y - clickY;
			if( sp < 0 )
			{
				sp = 0;
			}
			if( sp > sg )
			{
				sp = sg;
			}
			gadValue[ gadnum ] = sp > 0 ? sp * ( gadMax[ gadnum ] - gadRange[ gadnum ] ) / sg : 0;
			clickY += ( gadHeight[ gadnum ] - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ] - so;
			if( gadLink[ gadnum ] > 0 )
			{
				gadRedraw[ gadLink[ gadnum ] ] = true;
			}
			if( gadLink[ gadLink[ gadnum ] ] != gadnum )
			{
				gadRedraw[ gadnum ] = true;
			}
			repaint();
		}
	}
	
	private void dragHSlider( int gadnum, int x )
	{
		int ss = ( gadWidth[ gadnum ] - 12 ) * gadRange[ gadnum ] / gadMax[ gadnum ] + 8;
		int so = ( gadWidth[ gadnum ] - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ];
		int sg = gadWidth[ gadnum ] - 4 - ss;
		int sx = gadX[ gadnum ] + so + 2;
		if( clickX > sx && clickX < ( sx + ss ) )
		{
			int sp = so + x - clickX;
			if( sp < 0 )
			{
				sp = 0;
			}
			if( sp > sg )
			{
				sp = sg;
			}
			gadValue[ gadnum ] = sp > 0 ? sp * ( gadMax[ gadnum ] - gadRange[ gadnum ] ) / sg : 0;
			clickX += ( gadWidth[ gadnum ] - 12 ) * gadValue[ gadnum ] / gadMax[ gadnum ] - so;
			if( gadLink[ gadnum ] > 0 )
			{
				gadRedraw[ gadLink[ gadnum ] ] = true;
			}
			if( gadLink[ gadLink[ gadnum ] ] != gadnum )
			{
				gadRedraw[ gadnum ] = true;
			}
			repaint();
		}
	}
	
	private void drawListbox( Graphics g, int gadnum ) {
		int x = gadX[ gadnum ];
		int y = gadY[ gadnum ];
		int w = gadWidth[ gadnum ];
		int h = gadHeight[ gadnum ];
		int tw = ( w - 16 ) / 8;
		int th = ( h - 12 ) / 16;
		if( gadLink[ gadnum ] > 0 )
		{
			scrollListbox( gadnum, gadLink[ gadnum ] );
			drawVSlider( g, gadLink[ gadnum ] );
		}
		g.setColor( BACKGROUND );
		g.fillRect( x, y, w, h );
		lowerBox( g, x, y, w, h );
		int end = gadText[ gadnum ].length;
		if( gadValue[ gadnum ] + th > end )
		{
			th = end - gadValue[ gadnum ];
		}
		int ty = y + 6;
		for( int idx = gadValue[ gadnum ], len = idx + th; idx < len; idx++ ) {
			if( gadText[ gadnum ] != null && idx < gadText[ gadnum ].length )
			{
				String text = gadText[ gadnum ][ idx ];
				if( text.length() > tw )
				{
					text = text.substring( 0, tw );
				}
				else if( text.length() < tw )
				{
					char[] chars = new char[ tw ];
					text.getChars( 0, text.length(), chars, 0 );
					for( int c = text.length(); c < chars.length; c++ )
					{
						chars[ c ] = 32;
					}
					text = new String( chars );
				}
				
				int clr = TEXT_SHADOW_BACKGROUND;
				if( gadItem[ gadnum ] == idx )
				{
					clr = TEXT_HIGHLIGHT_SELECTED;
				}
				else if( gadValues[ gadnum ] != null && idx < gadValues[ gadnum ].length )
				{
					clr = gadValues[ gadnum ][ idx ];
				}
				drawText( g, x + 8, ty, text, clr );
			}
			ty += 16;
		}
	}
	
	private void clickListbox( int gadnum, boolean shift )
	{
		int time = ( int ) System.currentTimeMillis();
		int dt = time - gadRange[ gadnum ];
		int item = gadValue[ gadnum ] + ( clickY - gadY[ gadnum ] - 6 ) / 16;
		if( item == gadItem[ gadnum ] && dt > 0 && dt < 500 )
		{
			action( gadnum, shift );
			gadRange[ gadnum ] = 0;
		}
		else
		{
			if( item < gadText[ gadnum ].length )
			{
				gadItem[ gadnum ] = item;
			}
			gadRange[ gadnum ] = time;
			gadRedraw[ gadnum ] = true;
		}
	}
	
	private void keyListbox( int gadnum, char chr, int key, boolean shift )
	{
		int item = gadItem[ gadnum ];
		switch( key )
		{
			case KEY_UP:
				if( item > 0 )
				{
					gadItem[ gadnum ] = --item;
					int link = gadLink[ gadnum ] > 0 ? gadLink[ gadnum ] : gadnum;
					if( gadValue[ link ] > item )
					{
						gadValue[ link ] = item;
					}
					gadRedraw[ gadnum ] = true;
				}
				break;
			case KEY_DOWN:
				if( item < gadText[ gadnum ].length - 1 )
				{
					gadItem[ gadnum ] = ++item;
					int rows = ( gadHeight[ gadnum ] - 12 ) / 16;
					int link = gadLink[ gadnum ] > 0 ? gadLink[ gadnum ] : gadnum;
					if( gadValue[ link ] + rows <= item )
					{
						gadValue[ link ] = item - rows + 1;
					}
					gadRedraw[ gadnum ] = true;
				}
				break;
			default:
				if( chr == 10 )
				{
					action( gadnum, shift );
				}
				break;
		}
	}
	
	private void scrollListbox( int listbox, int slider )
	{
		gadRange[ slider ] = ( gadHeight[ listbox ] - 12 ) / 16;
		if( gadText[ listbox ] != null )
		{
			gadMax[ slider ] = gadText[ listbox ].length;
		}
		if( gadRange[ slider ] > gadMax[ slider ] )
		{
			gadRange[ slider ] = gadMax[ slider ];
		}
		if( gadValue[ slider ] + gadRange[ slider ] > gadMax[ slider ] )
		{
			gadValue[ slider ] = gadMax[ slider ] - gadRange[ slider ];
		}
		if( gadValue[ slider ] < 0 ) 
		{
			gadValue[ slider ] = 0;
		}
		gadValue[ listbox ] = gadValue[ slider ];
	}
	
	private void drawPattern( Graphics g, int gadnum )
	{
		int scroll = gadValue[ GADNUM_PATTERN_HSLIDER ];
		int pat = module.sequence[ ibxm.getSequencePos() ];
		int rows = module.patterns[ pat ].numRows;
		int x = gadX[ gadnum ];
		int y = gadY[ gadnum ];
		if( gadLink[ gadnum ] > 0 )
		{
			gadMax[ gadLink[ gadnum ] ] = rows + 14;
			gadValue[ gadnum ] = gadValue[ gadLink[ gadnum ] ];
			if( gadValue[ gadnum ] >= rows )
			{
				gadValue[ gadnum ] = rows - 1;
			}
			drawVSlider( g, gadLink[ gadnum ] );
		}
		drawInt( g, x, y, pat, 3, 7 );
		drawText( g, x + 3 * 8, y, " ", 7 );
		for( int c = 0; c < displayChannels; c++ )
		{
			int clr = ( ( mute >> ( c + scroll ) ) & 1 ) > 0 ? TEXT_RED : TEXT_BLUE;
			drawText( g, x + ( c * 11 + 4 ) * 8, y, clr == TEXT_RED ? " Muted     " : "Channel    ", clr );
			drawInt( g, x + ( c * 11 + 12 ) * 8, y, c + scroll + 1, 2, clr );
		}
		Note note = new Note();
		char[] chars = new char[ 10 ];
		for( int r = 1; r < 16; r++ )
		{
			int dr = gadValue[ gadnum ] - 8 + r;
			if( r == 15 && error != null )
			{
				String msg = error.length() > 11 * displayChannels ? error.substring( 0, 11 * displayChannels ) : error;
				drawText( g, x, y + r * 16, "*** " + pad( error, ' ', 11 * displayChannels, false ), TEXT_RED );
			}
			else if( dr < 0 || dr >= rows )
			{
				g.setColor( Color.BLACK );
				g.fillRect( x, y + r * 16, ( 4 + 11 * displayChannels ) * 8, 16 );
			}
			else
			{
				int hl = r == 8 ? 8 : 0;
				drawText( g, x, y + r * 16, "    ", TEXT_BLUE + hl );
				drawInt( g, x, y + r * 16, dr, 3, TEXT_BLUE + hl );
				for( int c = 0; c < displayChannels; c++ )
				{
					if( c + scroll >= module.numChannels )
					{
						drawText( g, x + ( c * 11 + 4 ) * 8, y + r * 16, "           ", TEXT_BLUE );
					}
					else
					{
						module.patterns[ pat ].getNote( dr * module.numChannels + c + scroll, note ).toChars( chars );
						if( ( ( mute >> ( c + scroll ) ) & 1 ) > 0 )
						{
							drawText( g, x + ( c * 11 + 4 ) * 8, y + r * 16, new String( chars ), TEXT_BLUE );
							drawText( g, x + ( c * 11 + 14 ) * 8, y + r * 16, " ", TEXT_BLUE );
						}
						else
						{
							int clr = chars[ 0 ] == '-' ? TEXT_BLUE : TEXT_CYAN;
							drawText( g, x + ( c * 11 + 4 ) * 8, y + r * 16, new String( chars, 0, 3 ), clr + hl );
							clr = chars[ 3 ] == '-' ? TEXT_BLUE : TEXT_RED;
							drawText( g, x + ( c * 11 + 7 ) * 8, y + r * 16, new String( chars, 3, 1 ), clr + hl );
							clr = chars[ 4 ] == '-' ? TEXT_BLUE : TEXT_RED;
							drawText( g, x + ( c * 11 + 8 ) * 8, y + r * 16, new String( chars, 4, 1 ), clr + hl );
							if( chars[ 5 ] >= '0' && chars[ 5 ] <= 'F' )
							{
								clr = VC_COLOURS[ chars[ 5 ] - '0' ];
							}
							else
							{
								clr = TEXT_BLUE;
							}
							drawText( g, x + ( c * 11 + 9 ) * 8, y + r * 16, new String( chars, 5, 2 ), clr + hl );
							if( chars[ 7 ] == 'E' && chars[ 8 ] >= '0' && chars[ 8 ] <= 'F' )
							{
								clr = EX_COLOURS[ chars[ 8 ] - '0' ];
							}
							else if( chars[ 7 ] == 's' && chars[ 8 ] >= '0' && chars[ 8 ] <= 'F' )
							{
								clr = SX_COLOURS[ chars[ 8 ] - '0' ];
							}
							else if( chars[ 7 ] >= '0' && chars[ 7 ] <= '~' )
							{
								clr = FX_COLOURS[ chars[ 7 ] - '0' ];
							}
							else
							{
								clr = TEXT_BLUE;
							}
							if( chars[ 7 ] >= 'a' )
							{
								chars[ 7 ] -= 32;
							}
							drawText( g, x + ( c * 11 + 11 ) * 8, y + r * 16, new String( chars, 7, 3 ), clr + hl );
							drawText( g, x + ( c * 11 + 14 ) * 8, y + r * 16, " ", clr + hl );
						}
					}
				}
			}
		}
	}
	
	private void clickPattern( int gadnum, boolean shift )
	{
		int scroll = gadValue[ GADNUM_PATTERN_HSLIDER ];
		int dspRow = ( clickY - gadY[ gadnum ] ) / 16;
		int dspCol = ( clickX - gadX[ gadnum ] ) / 8;
		int chn = scroll + ( dspCol - 4 ) / 11;
		int row = gadValue[ gadnum ] + dspRow - 8;
		long mask = 1 << chn;
		if( mute == ~mask || dspCol < 4 || chn >= module.numChannels )
		{
			/* Solo channel, unmute all. */
			mute = 0;
		}
		else if( ( mute & mask ) > 0 )
		{
			/* Muted channel, unmute. */
			mute ^= mask;
		}
		else
		{
			/* Unmuted channel, set as solo. */
			mute = -1 ^ mask;
		}
		ibxm.setMuted( -1, false );
		for( int idx = 0; idx < module.numChannels; idx++ )
		{
			ibxm.setMuted( idx, ( ( 1 << idx ) & mute ) != 0 );
		}
		gadRedraw[ GADNUM_PATTERN ] = true;
	}
	
	private int mapNoteKey( KeyEvent event )
	{
		int keyCode = event.getKeyCode();
		for( int idx = 0; idx < KEY_MAP.length; idx++ )
		{
			if( KEY_MAP[ idx ] == keyCode )
			{
				return idx + 1 + octave * 12;
			}
		}
		return -1;
	}
	
	private void trigger( int channel, int noteKey, boolean sustain )
	{
		if( !ibxm.getSequencerEnabled() )
		{
			if( noteKey < 1 || noteKey > 96 )
			{
				stop();
			}
			else if( keyChannel[ noteKey ] < 1 )
			{
				if( channel < 0 )
				{
					for( int chn = 0; chn < module.numChannels; chn++ )
					{
						triggerChannel = ( triggerChannel + 1 ) % module.numChannels;
						if( ( ( mute >> triggerChannel ) & 1 ) == 0 )
						{
							break;
						}
					}
					channel = triggerChannel;
				}
				Note note = new Note();
				note.key = noteKey;
				note.instrument = instrument;
				ibxm.trigger( channel, note );
				keyChannel[ noteKey ] = sustain ? 0 : channel + 1;
			}
		}
	}
	
	private void release( int noteKey )
	{
		if( !ibxm.getSequencerEnabled() && noteKey > 0 && noteKey < 97 )
		{
			int channel = keyChannel[ noteKey ] - 1;
			if( channel >= 0 )
			{
				Note note = new Note();
				note.key = 97;
				ibxm.trigger( channel, note );
				keyChannel[ noteKey ] = 0;
			}
		}
	}
	
	private int findGadget( int x, int y )
	{
		for( int idx = 0; idx < GAD_COUNT; idx++ )
		{
			if( gadType[ idx ] > 0 )
			{
				int x0 = gadX[ idx ];
				int y0 = gadY[ idx ];
				int x1 = x0 + gadWidth[ idx ];
				int y1 = y0 + gadHeight[ idx ];
				if( x >= x0 && y >= y0 && x < x1 && y < y1 )
				{
					return idx;
				}
			}
		}
		return 0;
	}

	private void createGadget( int gadnum, int type, int x, int y, int w, int h )
	{
		gadType[ gadnum ] = type;
		gadX[ gadnum ] = x;
		gadY[ gadnum ] = y;
		gadWidth[ gadnum ] = w;
		gadHeight[ gadnum ] = h;
	}
	
	private void createLabel( int gadnum, int x, int y, String text, int colour )
	{
		createGadget( gadnum, GAD_TYPE_LABEL, x, y, text.length() * 8, 16 );
		gadText[ gadnum ] = new String[] { text };
		gadValue[ gadnum ] = colour;
	}
	
	private void createButton( int gadnum, int x, int y, int w, int h, String text )
	{
		createGadget( gadnum, GAD_TYPE_BUTTON, x, y, w, h );
		gadText[ gadnum ] = new String[] { text };
	}
	
	private void createTextbox( int gadnum, int x, int y, int w, int h, String text )
	{
		createGadget( gadnum, GAD_TYPE_TEXTBOX, x, y, w, h );
		gadText[ gadnum ] = new String[] { text };
	}
	
	private void createVSlider( int gadnum, int x, int y, int w, int h, int range, int max )
	{
		createGadget( gadnum, GAD_TYPE_VSLIDER, x, y, w, h );
		gadValue[ gadnum ] = 0;
		gadRange[ gadnum ] = range;
		gadMax[ gadnum ] = max;
	}
	
	private void createHSlider( int gadnum, int x, int y, int w, int h, int range, int max )
	{
		createGadget( gadnum, GAD_TYPE_HSLIDER, x, y, w, h );
		gadValue[ gadnum ] = 0;
		gadRange[ gadnum ] = range;
		gadMax[ gadnum ] = max;
	}
	
	private void createListbox( int gadnum, int x, int y, int w, int h, int slider )
	{
		createGadget( gadnum, GAD_TYPE_LISTBOX, x, y, w, h );
		gadLink[ gadnum ] = slider;
		gadLink[ slider ] = gadnum;
	}
	
	private void createPattern( int gadnum, int x, int y, int channels, int slider )
	{
		createGadget( gadnum, GAD_TYPE_PATTERN, x, y, ( 4 + 11 * channels ) * 8, 16 * 16 );
		gadLink[ gadnum ] = slider;
		gadLink[ slider ] = gadnum;
	}
	
	private void escape( int gadnum )
	{
		switch( gadnum )
		{
			case GADNUM_TITLE_TEXTBOX:
				gadText[ gadnum ][ 0 ] = module.songName;
				break;
		}
	}
	
	private void action( int gadnum, boolean shift )
	{
		try
		{
			switch( gadnum ) 
			{
				case GADNUM_DIR_TEXTBOX:
					selectedFile = 0;
				case GADNUM_DIR_BUTTON:
					listDir( getDir() );
					gadItem[ GADNUM_DIR_LISTBOX ] = selectedFile;
					gadValue[ GADNUM_DIR_SLIDER ] = selectedFile - 3;
					break;
				case GADNUM_LOAD_BUTTON:
				case GADNUM_DIR_LISTBOX:
					if( gadValues[ GADNUM_DIR_LISTBOX ][ 0 ] == 0 )
					{
						setInstrument( gadItem[ GADNUM_DIR_LISTBOX ] + 1 );
					}
					else
					{
						File file = new File( gadText[ GADNUM_DIR_TEXTBOX ][ 0 ] );
						if( gadItem[ GADNUM_DIR_LISTBOX ] > 0 )
						{
							file = new File( file, gadText[ GADNUM_DIR_LISTBOX ][ gadItem[ GADNUM_DIR_LISTBOX ] ].substring( 6 ) );
							if( file.isDirectory() )
							{
								selectedFile = 0;
								listDir( file );
							}
							else
							{
								selectedFile = gadItem[ GADNUM_DIR_LISTBOX ];
								load( file );
							}
						}
						else
						{
							file = file.getParentFile();
							if( file != null )
							{
								selectedFile = 0;
								listDir( file );
							}
						}
					}
					break;
				case GADNUM_INST_INC_BUTTON:
					setInstrument( instrument + 1 );
					listInstruments();
					break;
				case GADNUM_INST_DEC_BUTTON:
					setInstrument( instrument - 1 );
					listInstruments();
					break;
				case GADNUM_TITLE_TEXTBOX:
					gadText[ gadnum ][ 0 ] = module.songName;
					break;
				case GADNUM_SEQ_LISTBOX:
					setSeqPos( gadItem[ gadnum ] );
					setRow( 0 );
					if( ibxm.getSequencerEnabled() )
					{
						ibxm.seekSequencePos( getSeqPos(), 0 );
					}
					else
					{
						ibxm.setSequencePos( getSeqPos() );
						stop();
					}
					break;
				case GADNUM_PLAY_BUTTON:
					if( ibxm.getSequencerEnabled() )
					{
						stop();
					}
					else
					{
						play();
					}
					break;
			}
		}
		catch( Exception e )
		{
			e.printStackTrace();
			setError( e.getMessage() );
		}
	}
	
	private static String[] getFileNames( File[] files, String[] names )
	{
		if( names != null )
		{
			names[ 0 ] = "[Parent Dir]";
		}
		int len = 1;
		for( int idx = 0; idx < files.length; idx++ )
		{
			File file = files[ idx ];
			if( !file.isHidden() && file.isDirectory() )
			{
				if( names != null )
				{
					names[ len ] = "[Dir] " + file.getName();
				}
				len++;
			}
		}
		for( int idx = 0; idx < files.length; idx++ )
		{
			File file = files[ idx ];
			if( !file.isHidden() && file.isFile() )
			{
				if( names != null )
				{
					String prefix;
					long size = file.length();
					if( size > 1048576 * 9216 ) 
					{
						prefix = "(>9g) ";
					}
					else if( size > 1024 * 9999 )
					{
						prefix = pad( Long.toString( size / 1048576 ), ' ', 4, true ) + "m ";
					}
					else if( size > 9999 )
					{
						prefix = pad( Long.toString( size / 1024 ), ' ', 4, true ) + "k ";
					}
					else
					{
						prefix = pad( Long.toString( size ), ' ', 5, true ) + " ";
					}
					names[ len ] = prefix + file.getName();
				}
				len++;
			}
		}
		return names != null ? names : getFileNames( files, new String[ len ] );
	}
	
	private File getDir()
	{
		File file = new File( gadText[ GADNUM_DIR_TEXTBOX ][ 0 ] );
		if( !file.isDirectory() )
		{
			file = new File( System.getProperty( "user.home" ) );
		}
		return file;
	}
	
	private void listDir( File file )
	{
		File[] files = file.listFiles();
		Arrays.sort( files );
		String[] names = getFileNames( files, null );
		int[] values = new int[ names.length ];
		for( int idx = 0; idx < names.length; idx++ )
		{
			values[ idx ] = names[ idx ].charAt( 0 ) == '[' ? 1 : 0;
		}
		gadText[ GADNUM_DIR_TEXTBOX ][ 0 ] = file.getAbsolutePath();
		gadText[ GADNUM_DIR_LISTBOX ] = names;
		gadItem[ GADNUM_DIR_LISTBOX ] = 0;
		gadValue[ GADNUM_DIR_SLIDER ] = 0;
		gadValues[ GADNUM_DIR_LISTBOX ] = values;
		gadText[ GADNUM_INST_LISTBOX ] = KEYS;
		gadRedraw[ GADNUM_DIR_TEXTBOX ] = true;
		gadRedraw[ GADNUM_DIR_LISTBOX ] = true;
		gadRedraw[ GADNUM_INST_LISTBOX ] = true;
	}
	
	private static int parsePositiveInt( String str, int max )
	{
		int value = 0;
		for( int idx = 0, len = str.length(); idx < len; idx++ )
		{
			char chr = str.charAt( idx );
			if( chr >= '0' && chr <= '9' )
			{
				value = value * 10 + chr - '0';
			}
		}
		return value > max ? max : value;
	}
	
	private void setSequence( int[] sequence )
	{
		String[] items = new String[ sequence.length ];
		for( int idx = 0; idx < items.length; idx++ )
		{
			String pat = String.valueOf( sequence[ idx ] );
			items[ idx ] = pad( String.valueOf( idx ), '0', 3, true )
				+ ' ' + pad( pat, ' ', 3, true );
		}
		gadText[ GADNUM_SEQ_LISTBOX ] = items;
		module.sequence = sequence;
		int seqPos = getSeqPos();
		if( seqPos >= sequence.length )
		{
			seqPos = sequence.length - 1;
		}
		setSeqPos( seqPos );
	}
	
	private int getRow()
	{
		return gadValue[ GADNUM_PATTERN_VSLIDER ];
	}
	
	private void setRow( int row )
	{
		gadValue[ GADNUM_PATTERN_VSLIDER ] = row;
		gadRedraw[ GADNUM_PATTERN ] = true;
	}
	
	private int getSeqPos()
	{
		return gadItem[ GADNUM_SEQ_LISTBOX ];
	}
	
	private void setSeqPos( int seqPos )
	{
		if( seqPos >= module.sequenceLength )
		{
			seqPos = 0;
		}
		gadItem[ GADNUM_SEQ_LISTBOX ] = seqPos;
		gadRedraw[ GADNUM_SEQ_LISTBOX ] = true;
		gadRedraw[ GADNUM_PATTERN ] = true;
	}
	
	private void setInstrument( int idx )
	{
		if( idx < 1 )
		{
			idx = 1;
		}
		if( idx > module.numInstruments )
		{
			idx = module.numInstruments;
		}
		instrument = idx;
		gadText[ GADNUM_INST_TEXTBOX ][ 0 ] = String.valueOf( instrument );
		StringBuffer sb = new StringBuffer();
		module.instruments[ instrument ].toStringBuffer( sb );
		gadText[ GADNUM_INST_LISTBOX ] = split( sb.toString(), '\n' );
		gadRedraw[ GADNUM_INST_TEXTBOX ] = true;
		gadRedraw[ GADNUM_INST_LISTBOX ] = true;
	}
	
	private void listInstruments()
	{
		int cols = ( gadWidth[ GADNUM_DIR_LISTBOX ] - 16 ) / 8;
		String[] names = new String[ module.numInstruments ];
		for( int ins = 1; ins <= names.length; ins++ )
		{
			Sample sam = module.instruments[ ins ].samples[ 0 ];
			String name = pad( module.instruments[ ins ].name, ' ', cols - 11, false );
			String len = pad( String.valueOf( sam.getLoopStart() + sam.getLoopLength() ), ' ', 7, true );
			names[ ins - 1 ] = pad( String.valueOf( ins ), '0', 3, true ) + ' ' + name + len;
		}
		gadValues[ GADNUM_DIR_LISTBOX ] = new int[ names.length ];
		gadItem[ GADNUM_DIR_LISTBOX ] = instrument - 1;
		gadText[ GADNUM_DIR_LISTBOX ] = names;
		gadValue[ GADNUM_DIR_SLIDER ] = instrument - 4;
		gadRedraw[ GADNUM_DIR_LISTBOX ] = true;
	}
	
	private void play()
	{
		error = null;
		ibxm.setSequencerEnabled( true );
		ibxm.seekSequencePos( getSeqPos(), gadValue[ GADNUM_PATTERN ] );
		gadText[ GADNUM_PLAY_BUTTON ][ 0 ] = "Stop";
		gadRedraw[ GADNUM_PLAY_BUTTON ] = true;
	}
	
	private void stop()
	{
		Note note = new Note();
		/* Set global volume 64. */
		note.effect = 0x10;
		note.param = 0x100;
		ibxm.trigger( 0, note );
		ibxm.setSequencerEnabled( false );
		for( int idx = 0; idx < module.numChannels; idx++ )
		{
			/* Set channel volume 0 and stereo panning. */
			note.volume = 0x10;
			note.effect = 0x8;
			note.param = ( ( idx & 3 ) == 1 || ( idx & 3 ) == 2 ) ? 204 : 51;
			ibxm.trigger( idx, note );
		}
		for( int idx = 0; idx < keyChannel.length; idx++ )
		{
			keyChannel[ idx ] = 0;
		}
		gadText[ GADNUM_PLAY_BUTTON ][ 0 ] = "Play";
		gadRedraw[ GADNUM_PLAY_BUTTON ] = true;
	}
	
	private void load( File file ) throws IOException
	{
		System.out.println( "Load " + file.getAbsolutePath() );
		FileInputStream inputStream = new FileInputStream( file );
		try
		{
			Module module = new Module( new Data( inputStream ) );
			IBXM ibxm = new IBXM( module, SAMPLING_RATE );
			ibxm.setInterpolation( this.ibxm.getInterpolation() );
			this.module = module;
			this.ibxm = ibxm;
		}
		finally
		{
			inputStream.close();
		}
		int[] sequence = new int[ module.sequenceLength ];
		System.arraycopy( module.sequence, 0, sequence, 0, sequence.length );
		setSequence( sequence );
		gadItem[ GADNUM_PATTERN ] = 0;
		gadText[ GADNUM_TITLE_TEXTBOX ][ 0 ] = module.songName.trim();
		gadRedraw[ GADNUM_TITLE_TEXTBOX ] = true;
		setSeqPos( 0 );
		gadValue[ GADNUM_SEQ_SLIDER ] = 0;
		setRow( 0 );
		gadValue[ GADNUM_DIR_SLIDER ] = 0;
		int max = module.numChannels;
		if( max < displayChannels )
		{
			max = displayChannels;
		}
		gadMax[ GADNUM_PATTERN_HSLIDER ] = max;
		gadValue[ GADNUM_PATTERN_HSLIDER ] = 0;
		gadRedraw[ GADNUM_PATTERN_HSLIDER ] = true;
		setInstrument( 1 );
		listInstruments();
		mute = 0;
		stop();
	}
	
	private synchronized int getAudio( int[] output )
	{
		int count = ibxm.getAudio( output );
		if( ibxm.getSequencerEnabled() && focus != GADNUM_PATTERN_VSLIDER )
		{
			int seqPos = ibxm.getSequencePos();
			int row = ibxm.getRow();
			if( seqPos != getSeqPos() || row != getRow() )
			{
				int dt = ( ( int ) System.currentTimeMillis() ) - gadRange[ GADNUM_SEQ_LISTBOX ];
				if( seqPos != getSeqPos() && ( dt < 0 || dt > 500 ) )
				{
					setSeqPos( seqPos );
					gadValue[ GADNUM_SEQ_SLIDER ] = seqPos - 4;
				}
				setRow( row );
				repaint();
			}
		}
		return count;
	}
	
	public static void main( String[] args ) throws Exception
	{
		int channels = 10;
		if( args.length > 0 )
		{
			channels = parsePositiveInt( args[ 0 ], 32 );
			if( channels < 8 )
			{
				channels = 8;
			}
		}
		IBXMPlayer3 ibxmPlayer3 = new IBXMPlayer3( channels );
		Frame frame = new Frame( "IBXM " + IBXM.VERSION );
		frame.setIconImage( ibxmPlayer3.iconImage() );
		frame.addWindowListener( ibxmPlayer3 );
		frame.add( ibxmPlayer3, BorderLayout.CENTER );
		frame.pack();
		frame.setResizable( false );
		frame.setVisible( true );
		javax.sound.sampled.AudioFormat audioFormat = new javax.sound.sampled.AudioFormat( SAMPLING_RATE, 16, 2, true, false );
		javax.sound.sampled.SourceDataLine sourceDataLine = ( javax.sound.sampled.SourceDataLine )
			javax.sound.sampled.AudioSystem.getLine( new javax.sound.sampled.DataLine.Info(
				javax.sound.sampled.SourceDataLine.class, audioFormat ) );
		sourceDataLine.open( audioFormat, 16384 );
		try
		{
			sourceDataLine.start();
			int[] reverbBuf = new int[ ( SAMPLING_RATE / 20 ) * 2 ];
			int[] mixBuf = new int[ ibxmPlayer3.ibxm.getMixBufferLength() ];
			byte[] outBuf = new byte[ mixBuf.length * 2 ];
			int mixIdx = 0, mixLen = 0, reverbIdx = 0;
			while( frame.isDisplayable() )
			{
				int count = ibxmPlayer3.getAudio( mixBuf );
				if( ibxmPlayer3.reverb ) {
					reverbIdx = reverb( mixBuf, reverbBuf, reverbIdx, count );
				}
				clip( mixBuf, outBuf, count * 2 );
				sourceDataLine.write( outBuf, 0, count * 4 );
			}
		}
		finally
		{
			sourceDataLine.close();
		}
	}
}
