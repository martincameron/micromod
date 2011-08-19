
package micromod;

import java.io.InputStream;

/*
	An InputStream that produces 16-bit WAV audio data from a Micromod instance.
	J2ME: java.microedition.media.Manager.createPlayer( wavInputStream, "audio/x-wav" ).start();
*/
public class WavInputStream extends InputStream {
	private static final byte[] header = {
		0x52, 0x49, 0x46, 0x46, /* "RIFF" */
		0x00, 0x00, 0x00, 0x00, /* Audio data size + 36 */
		0x57, 0x41, 0x56, 0x45, /* "WAVE" */
		0x66, 0x6D, 0x74, 0x20, /* "fmt " */
		0x10, 0x00, 0x00, 0x00, /* 16 (bytes to follow) */
		0x01, 0x00, 0x02, 0x00, /* 1 (pcm), 2 (stereo) */
		0x00, 0x00, 0x00, 0x00, /* Sample rate */
		0x00, 0x00, 0x00, 0x00, /* Sample rate * 4 */
		0x04, 0x00, 0x10, 0x00, /* 4 (bytes/frame), 16 (bits) */
		0x64, 0x61, 0x74, 0x61, /* "data" */
		0x00, 0x00, 0x00, 0x00, /* Audio data size */
	};

	private Micromod micromod;
	private int[] mixBuf;
	private byte[] outBuf;
	private int duration, outIdx, outLen;

	public WavInputStream( Micromod micromod ) {
		this.micromod = micromod;
		mixBuf = new int[ micromod.getMixBufferLength() ];
		outBuf = new byte[ mixBuf.length * 4 ];
		duration = micromod.calculateSongDuration();
		int samplingRate = micromod.getSampleRate();
		System.arraycopy( header, 0, outBuf, 0, header.length );
		writeInt32( outBuf, 4, duration * 4 + 36 );
		writeInt32( outBuf, 24, samplingRate );
		writeInt32( outBuf, 28, samplingRate * 4 );
		writeInt32( outBuf, 40, duration * 4 );
		outIdx = 0;
		outLen = 44;
	}

	public int getWavFileLength() {
		return duration * 4 + header.length;
	}

	public int read() {
		int out = outBuf[ outIdx++ ];
		if( outIdx >= outLen ) {
			getAudio();
		}
		return out;
	}

	public int read( byte[] buf, int off, int len ) {
		int remain = outLen - outIdx;
		if( len > remain ) {
			len = remain;
		}
		System.arraycopy( outBuf, outIdx, buf, off, len );
		outIdx += len;
		if( outIdx >= outLen ) {
			getAudio();
		}
		return len;
	}

	private void getAudio() {
		int mEnd = micromod.getAudio( mixBuf ) * 2;
		for( int mIdx = 0, oIdx = 0; mIdx < mEnd; mIdx++ ) {
			int ampl = mixBuf[ mIdx ];
			outBuf[ oIdx++ ] = ( byte ) ampl;
			outBuf[ oIdx++ ] = ( byte ) ( ampl >> 8 );
		}
		outIdx = 0;
		outLen = mEnd * 2;
	}

	private static void writeInt32( byte[] buf, int idx, int value ) {
		buf[ idx ] = ( byte ) value;
		buf[ idx + 1 ] = ( byte ) ( value >> 8 );
		buf[ idx + 2 ] = ( byte ) ( value >> 16 );
		buf[ idx + 3 ] = ( byte ) ( value >> 24 );
	}

	/* Simple Mod to Wav converter. */
	public static void main( String[] args ) throws java.io.IOException {
		if( args.length > 0 ) {
			// Parse arguments.
			String modFileName = args[ 0 ];
			String wavFileName = modFileName + ".wav";
			int sampleRate = 48000;
			boolean interpolation = false;
			for( int idx = 1; idx < args.length; idx++ ) {
				String arg = args[ idx ];
				if( arg.startsWith( "rate=" ) ) {
					sampleRate = Integer.parseInt( arg.substring( 5 ) );
				} else if( "int=linear".equals( arg ) ) {
					interpolation = true;
				} else {
					wavFileName = arg;
				}
			}
			// Load module data into array.
			java.io.File modFile = new java.io.File( modFileName );
			byte[] buf = new byte[ ( int ) modFile.length() ];
			InputStream in = new java.io.FileInputStream( modFile );
			int idx = 0;
			while( idx < buf.length ) {
				int len = in.read( buf, idx, buf.length - idx );
				if( len < 0 ) throw new java.io.IOException( "Unexpected end of file." );
				idx += len;
			}
			in.close();
			// Write WAV file to output.
			java.io.OutputStream out = new java.io.FileOutputStream( wavFileName );
			Micromod micromod = new Micromod( new Module( buf ), sampleRate );
			micromod.setInterpolation( interpolation );
			in = new WavInputStream( micromod );
			buf = new byte[ micromod.getMixBufferLength() * 4 ];
			int remain = ( ( WavInputStream ) in ).getWavFileLength();
			while( remain > 0 ) {
				int count = remain > buf.length ? buf.length : remain;
				count = in.read( buf, 0, count );
				out.write( buf, 0, count );
				remain -= count;
			}		
			out.close();
		} else {
			System.err.println( "Mod to Wav converter." );
			System.err.println( "Usage: java " + WavInputStream.class.getName() + " input.mod [output.wav] [rate=16000-96000] [int=linear]" );
		}
	}
}
