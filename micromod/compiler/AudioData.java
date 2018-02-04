
package micromod.compiler;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioData {
	private static final int
		FP_SHIFT = 7, FP_ONE = 1 << FP_SHIFT, FP_MASK = FP_ONE - 1, HTAPS = 32;
	
	private int sampleRate;
	private short[] sampleData;

	public AudioData( short[] samples, int samplingRate ) {
		this.sampleData = samples;
		setSamplingRate( samplingRate );
	}

	public AudioData( byte[] samples, int length, int samplingRate ) {
		this.sampleData = new short[ length ];
		for( int idx = 0; idx < length; idx++ ) {
			this.sampleData[ idx ] = ( short ) ( samples[ idx ] << 8 );
		}
		setSamplingRate( samplingRate );
	}

	public AudioData( byte[] samples, int samplingRate ) {
		this( samples, samples.length, samplingRate );
	}

	public int getSamplingRate() {
		return sampleRate;
	}

	public void setSamplingRate( int samplingRate ) {
		this.sampleRate = samplingRate;
	}

	public int getLength() {
		return sampleData.length;
	}

	public short[] getSampleData() {
		return sampleData;
	}

	/* Read a mono sample file.
	   If the sample format and sampling rate cannot be determined,
	   and maxLength is greater than zero, raw 8-bit signed audio up
	   to maxLength bytes are read and a sampling rate of 8000hz assumed. */
	public static AudioData readSam( InputStream inputStream, int maxLength ) throws IOException {
		InputStream input = new java.io.BufferedInputStream( inputStream, 65536 );
		input.mark( 4 );
		char[] chars = new char[ 4 ];
		readChars( input, chars, 4 );
		input.reset();
		String chunkId = new String( chars );
		if( "RIFF".equals( chunkId ) ) {
			return readWav( input, 0 );
		} else if( "FORM".equals( chunkId ) ) {
			return readIff( input );
		} else if( maxLength > 0 ) {
			return readRaw( input, maxLength, 8000 );
		} else {
			throw new IllegalArgumentException( "Unsupported file format." );
		}
	}

	/* Read raw 8-bit signed audio data up to maxLength bytes. */
	public static AudioData readRaw( InputStream inputStream, int maxLength, int samplingRate ) throws IOException {
		byte[] inputBuf = new byte[ maxLength ];
		int length = readFully( inputStream, inputBuf, maxLength );
		return new AudioData( inputBuf, length, samplingRate );
	}

	/* Read an uncompressed IFF-8SVX file. */
	public static AudioData readIff( InputStream inputStream ) throws IOException {
		char[] chunkId = new char[ 4 ];
		readChars( inputStream, chunkId, 4 );
		if( !"FORM".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "FORM chunk not found." );
		}
		int chunkSize = readIntBe( inputStream );
		readChars( inputStream, chunkId, 4 );
		if( !"8SVX".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "8SVX chunk not found." );
		}
		readChars( inputStream, chunkId, 4 );
		if( !"VHDR".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "VHDR chunk not found." );
		}
		chunkSize = readIntBe( inputStream );
		int attackLen = readIntBe( inputStream );
		int sustainLen = readIntBe( inputStream );
		int samplesHigh = readIntBe( inputStream );
		int sampleRate = readShortBe( inputStream ) & 0xFFFF;
		int numOctaves = inputStream.read();
		int compression = inputStream.read();
		if( compression != 0 ) {
			throw new IllegalArgumentException( "Compressed IFF not supported." );
		}
		int volume = readIntBe( inputStream );
		readChars( inputStream, chunkId, 4 );
		while( !"BODY".equals( new String( chunkId ) ) ) {
			chunkSize = readIntBe( inputStream );
			inputStream.skip( chunkSize );
			readChars( inputStream, chunkId, 4 );
		}
		int numSamples = readIntBe( inputStream );
		short[] sampleData = new short[ numSamples ];
		byte[] inputBuf = new byte[ numSamples ];
		int len = readFully( inputStream, inputBuf, numSamples );
		for( int idx = 0; idx < len; idx++ ) {
			sampleData[ idx ] = ( short ) ( inputBuf[ idx ] << 8 );
		}
		return new AudioData( sampleData, sampleRate );
	}

	/* Read a Wav file. Channel is 0 for left/mono.*/
	public static AudioData readWav( InputStream inputStream, int channel ) throws IOException {
		char[] chunkId = new char[ 4 ];
		readChars( inputStream, chunkId, 4 );
		if( !"RIFF".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Riff header not found." );
		}
		int chunkSize = readInt( inputStream );
		readChars( inputStream, chunkId, 4 );
		if( !"WAVE".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Wave header not found." );
		}
		readChars( inputStream, chunkId, 4 );
		if( !"fmt ".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Format header not found." );
		}
		chunkSize = readInt( inputStream );
		int format = readShort( inputStream );
		int numChannels = readShort( inputStream );
		if( channel < 0 || channel >= numChannels ) {
			throw new IllegalArgumentException( "No such channel: " + channel );
		}
		int sampleRate = readInt( inputStream );
		int bytesPerSec = readInt( inputStream );
		int bytesPerSample = readShort( inputStream );
		int bytesPerChannel = bytesPerSample / numChannels;
		int bitsPerSample = readShort( inputStream );
		if( bitsPerSample > 24 ) {
			format = 0;
		}
		if( format == 0xFFFE ) {
			int blockSize = readShort( inputStream );
			int validBits = readShort( inputStream );
			int channelMask = readInt( inputStream );
			char[] formatId = new char[ 16 ];
			readChars( inputStream, formatId, 16 );
			String pcmId = "\u0001\u0000\u0000\u0000\u0000\u0000\u0010\u0000\u0080\u0000\u0000\u00AA\u0000\u0038\u009B\u0071";
			format = pcmId.equals( new String( formatId ) ) ? 1 : 0;
			inputStream.skip( chunkSize - 40 );
		} else {
			inputStream.skip( chunkSize - 16 );
		}
		if( format != 1 ) {
			throw new IllegalArgumentException( "Unsupported sample format." );
		}
		readChars( inputStream, chunkId, 4 );
		while( !"data".equals( new String( chunkId ) ) ) {
			//System.err.println( "Ignoring chunk: " + new String( chunkId ) );
			chunkSize = readInt( inputStream );
			inputStream.skip( chunkSize );
			readChars( inputStream, chunkId, 4 );
		}
		int numSamples = readInt( inputStream ) / bytesPerSample;
		short[] sampleData = new short[ numSamples ];
		byte[] inputBuf = new byte[ numSamples * bytesPerSample ];
		int outputEnd = readFully( inputStream, inputBuf, numSamples * bytesPerSample ) / bytesPerSample;
		int inputIdx = channel * bytesPerChannel, outputIdx = 0;
		switch( bytesPerChannel ) {
			case 1: // 8-bit unsigned.
				while( outputIdx < outputEnd ) {
					sampleData[ outputIdx++ ] = ( short ) ( ( ( inputBuf[ inputIdx ] & 0xFF ) - 128 ) << 8 );
					inputIdx += bytesPerSample;
				}
				break;
			case 2: // 16-bit signed little-endian.
				while( outputIdx < outputEnd ) {
					sampleData[ outputIdx++ ] = ( short ) ( ( inputBuf[ inputIdx ] & 0xFF ) | ( inputBuf[ inputIdx + 1 ] << 8 ) );
					inputIdx += bytesPerSample;
				}
				break;
			case 3: // 24-bit signed little-endian.
				while( outputIdx < outputEnd ) {
					sampleData[ outputIdx++ ] = ( short ) ( ( inputBuf[ inputIdx + 1 ] & 0xFF ) | ( inputBuf[ inputIdx + 2 ] << 8 ) );
					inputIdx += bytesPerSample;
				}
				break;
		}
		return new AudioData( sampleData, sampleRate );
	}
	
	public void writeWav( OutputStream outputStream, boolean quantize ) throws IOException {
		int frameSize = quantize ? 1 : 2;
		writeChars( outputStream, "RIFF".toCharArray(), 4 );
		writeInt( outputStream, sampleData.length * frameSize + 36 ); // Wave chunk length.
		writeChars( outputStream, "WAVE".toCharArray(), 4 );
		writeChars( outputStream, "fmt ".toCharArray(), 4 );
		writeInt( outputStream, 16 ); // Format chunk length.
		writeShort( outputStream, 1 ); // PCM format.
		writeShort( outputStream, 1 ); // Mono.
		writeInt( outputStream, sampleRate );
		writeInt( outputStream, sampleRate * frameSize ); // Bytes per sec.
		writeShort( outputStream, frameSize ); // Frame size.
		writeShort( outputStream, frameSize * 8 ); // Bits per sample.
		writeChars( outputStream, "data".toCharArray(), 4 );
		writeInt( outputStream, sampleData.length * frameSize ); // PCM data length.
		if( quantize ) {
			byte[] outputBuf = quantize();
			for( int outputIdx = 0; outputIdx < outputBuf.length; outputIdx++ ) {
				outputBuf[ outputIdx ] = ( byte ) ( outputBuf[ outputIdx ] + 128 );
			}
			outputStream.write( outputBuf, 0, outputBuf.length );
		} else {
			byte[] outputBuf = new byte[ sampleData.length * 2 ];
			for( int outputIdx = 0; outputIdx < outputBuf.length; outputIdx += 2 ) {
				int amp = sampleData[ outputIdx >> 1 ];
				outputBuf[ outputIdx ] = ( byte ) amp;
				outputBuf[ outputIdx + 1 ] = ( byte ) ( amp >> 8 );
			}
			outputStream.write( outputBuf, 0, outputBuf.length );
		}
	}

	public byte[] quantize() {
		byte[] outputBuf = new byte[ sampleData.length ];
		int qerror = 0;
		for( int idx = 0, end = sampleData.length; idx < end; idx++ ) {
			int ampl = sampleData[ idx ];
			/* Dithering. */
			ampl -= qerror;
			qerror = ampl;
			/* Rounding. */
			ampl += ampl & 0x80;
			ampl = ampl / 256;
			qerror = ( ampl << 8 ) - qerror;
			/* Clipping. */
			if( ampl < -128 ) ampl = -128;
			if( ampl > 127 ) ampl = 127;
			outputBuf[ idx ] = ( byte ) ampl;
		}
		return outputBuf;
	}

	public AudioData crop( int offset, int length ) {
		short[] samples = new short[ length ];
		System.arraycopy( sampleData, offset, samples, 0, length );
		return new AudioData( samples, sampleRate );
	}

	public AudioData scale( int volume ) {
		int len = sampleData.length;
		short[] buf = new short[ len ];
		for( int idx = 0; idx < len; idx++ ) {
			int a = ( sampleData[ idx ] * volume ) >> 6;
			if( a < -32768 ) a = -32768;
			if( a >  32767 ) a =  32767;
			buf[ idx ] = ( short ) a;
		}
		return new AudioData( buf, this.sampleRate );
	}

	public AudioData resample( int samplingRate ) {
		return resample( samplingRate, 0 );
	}

	/* Return an AudioData instance with the specified sampling rate,
	   optionally adjusted in pitch by 96 per octave increments. */
	public AudioData resample( int samplingRate, int pitch ) {
		short[] inputBuf = new short[ sampleData.length + HTAPS * 2 ];
		for( int idx = 0; idx < sampleData.length; idx++ ) {
			inputBuf[ idx + HTAPS - 1 ] = sampleData[ idx ];
		}
		int step = ( int ) Math.round( this.sampleRate * Math.pow( 2, pitch / 96.0 ) * FP_ONE / samplingRate );
		int outputLen = ( sampleData.length << FP_SHIFT ) / step;
		short[] outputBuf = new short[ outputLen ];
		float[] sinc = sincTable( step > FP_ONE ? FP_ONE / ( double ) step : 1.0 );
		int inputIdx = 0;
		for( int outputIdx = 0; outputIdx < outputLen; outputIdx++ ) {
			float a = 0;
			for( int tap = 0; tap < HTAPS; tap++ ) {
				a = a + inputBuf[ ( inputIdx >> FP_SHIFT ) + tap ] * sinc[ ( ( HTAPS - tap - 1 ) << FP_SHIFT ) + ( inputIdx & FP_MASK ) ];
				a = a + inputBuf[ ( inputIdx >> FP_SHIFT ) + tap + HTAPS ] * sinc[ ( ( tap + 1 ) << FP_SHIFT ) - ( inputIdx & FP_MASK ) ];
			}
			if( a < -32768 ) a = -32768;
			if( a >  32767 ) a =  32767;
			outputBuf[ outputIdx ] = ( short ) a;
			inputIdx += step;
		}
		return new AudioData( outputBuf, samplingRate );
	}

	private static float[] sincTable( double bandwidth ) {
		int len = HTAPS * FP_ONE;
		float[] tab = new float[ len + 1 ];
		tab[ 0 ] = ( float ) bandwidth;
		for( int idx = 1; idx < len; idx++ ) {
			double pit = Math.PI * idx / FP_ONE;
			/* Blackman-Harris window function. */
			double w = 2 * Math.PI * ( 0.5 + ( idx * 0.5 / len ) );
			double y = 0.35875 - 0.48829 * Math.cos( w ) + 0.14128 * Math.cos( w * 2 ) - 0.01168 * Math.cos( w * 3 );
			tab[ idx ] = ( float ) ( y * Math.sin( pit * bandwidth ) / pit );
		}
		return tab;
	}

	private static int readInt( InputStream input ) throws IOException {
		return readShort( input ) | ( readShort( input ) << 16 );
	}

	private static int readIntBe( InputStream input ) throws IOException {
		return ( readShortBe( input ) << 16 ) | readShortBe( input );
	}

	private static void writeInt( OutputStream output, int value ) throws IOException {
		writeShort( output, value );
		writeShort( output, value >> 16 );
	}

	private static int readShort( InputStream input ) throws IOException {
		return ( input.read() & 0xFF ) | ( ( input.read() & 0xFF ) << 8 );
	}

	private static int readShortBe( InputStream input ) throws IOException {
		return ( ( input.read() & 0xFF ) << 8 ) | ( input.read() & 0xFF );
	}
	
	private static void writeShort( OutputStream output, int value ) throws IOException {
		output.write( ( byte ) value );
		output.write( ( byte ) ( value >> 8 ) );
	}
	
	private static void readChars( InputStream input, char[] chars, int length ) throws IOException {
		for( int idx = 0; idx < length; idx++ ) {
			chars[ idx ] = ( char ) ( input.read() & 0xFF );
		}
	}
	
	private static void writeChars( OutputStream output, char[] chars, int length ) throws IOException {
		for( int idx = 0; idx < length; idx++ ) {
			output.write( ( byte ) chars[ idx ] );
		}
	}
	
	private static int readFully( InputStream input, byte[] inputBuf, int inputBytes ) throws IOException {
		int inputIdx = 0, inputRead = 0;
		while( inputIdx < inputBytes && inputRead >= 0 ) {
			inputIdx += inputRead;
			inputRead = input.read( inputBuf, inputIdx, inputBytes - inputIdx );
		}
		return inputIdx;
	}

	/* Simple converter program. */
	public static void main( String[] args ) throws IOException {
		boolean quantize = false;
		String inputPath = null, outputPath = null;
		int channel = 0, offset = 0, length = 0, rate = 0, tune = 96, gain = 64, idx = 0;
		while( idx < args.length ) {
			String arg = args[ idx++ ];
			if( "-chan".equals( arg ) ) {
				channel = Integer.parseInt( args[ idx++ ] );
			} else if( "-crop".equals( arg ) ) {
				String str = args[ idx++ ];
				int sep = str.indexOf( ',' );
				if( sep < 0 ) {
					length = Integer.parseInt( str );
				} else {
					offset = Integer.parseInt( str.substring( 0, sep ) );
					length = Integer.parseInt( str.substring( sep + 1 ) );
				}
			} else if( "-tune".equals( arg ) ) {
				tune = Integer.parseInt( args[ idx++ ] );
			} else if( "-rate".equals( arg ) ) {
				rate = Integer.parseInt( args[ idx++ ] );
			} else if( "-gain".equals( arg ) ) {
				gain = Integer.parseInt( args[ idx++ ] );
			} else if( "-8bit".equals( arg ) ) {
				quantize = true;
			} else if( inputPath == null ) {
				inputPath = arg;
			} else if( outputPath == null ) {
				outputPath = arg;
			}
		}
		if( inputPath != null ) {
			AudioData audioData = readWav( new java.io.FileInputStream( inputPath ), channel );
			System.out.println( "Input Length: " + audioData.getLength() + " samples.");
			if( outputPath != null ) {
				if( length > 0 ) {
					audioData = audioData.crop( offset, length );
				}
				if( rate > 0 || tune != 96 ) {
					audioData = audioData.resample( rate > 0 ? rate : audioData.getSamplingRate(), tune - 96 );
				}
				if( gain > 0 && gain != 64 ) {
					audioData = audioData.scale( gain );
				}
				OutputStream outputStream = new java.io.FileOutputStream( outputPath );
				try {
					audioData.writeWav( outputStream, quantize );
				} finally {
					outputStream.close();
				}
			}
		} else {
			System.err.println( "Usage: java " + AudioData.class.getName() + " input.wav [output.wav] [-chan 0] [-crop 0,len] [-tune 96] [-rate hz] [-gain 64] [-8bit]" );
		}
	}
}
