
package micromod.tracker;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioData {
	private static final int
		FP_SHIFT = 7, FP_ONE = 1 << FP_SHIFT, FP_MASK = FP_ONE - 1, HTAPS = 32;
	
	private int sampleRate;
	private short[] sampleData;

	public AudioData( short[] samples, int samplingRate ) {
		this.sampleRate = samplingRate;
		this.sampleData = samples;
	}

	public AudioData( byte[] samples, int samplingRate ) {
		this.sampleRate = samplingRate;
		this.sampleData = new short[ samples.length ];
		for( int idx = 0; idx < samples.length; idx++ ) {
			this.sampleData[ idx ] = ( short ) ( samples[ idx ] << 8 );
		}
	}

	public int getSamplingRate() {
		return sampleRate;
	}

	public int getLength() {
		return sampleData.length;
	}

	public short[] getSampleData() {
		return sampleData;
	}

	/* Read a Wav file. Channel is 0 for left/mono.*/
	public AudioData( InputStream inputStream, int channel ) throws IOException {
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
		sampleRate = readInt( inputStream );
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
		sampleData = new short[ numSamples ];
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
	}
	
	public void writeWav( OutputStream outputStream, boolean quantize ) throws IOException {
		writeChars( outputStream, "RIFF".toCharArray(), 4 );
		writeInt( outputStream, sampleData.length * 2 + 36 ); // Wave chunk length.
		writeChars( outputStream, "WAVE".toCharArray(), 4 );
		writeChars( outputStream, "fmt ".toCharArray(), 4 );
		writeInt( outputStream, 16 ); // Format chunk length.
		writeShort( outputStream, 1 ); // PCM format.
		writeShort( outputStream, 1 ); // Mono.
		writeInt( outputStream, sampleRate );
		int frameSize = quantize ? 1 : 2;
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
		boolean noiseShaping = false;
		for( int idx = 0; idx < sampleData.length; idx++ ) {
			/* Determine whether source is already quantized. */
			noiseShaping |= ( sampleData[ idx ] & 0xFF ) > 0;
		}
		byte[] outputBuf = new byte[ sampleData.length ];
		if( noiseShaping ) {
			int rand = 0, s1 = 0, s2 = 0, s3 = 0;
			for( int idx = 0, end = sampleData.length; idx < end; idx++ ) {
				// Convert to unsigned for proper integer rounding.
				int in = sampleData[ idx ] + 32768;
				// TPDF dither.
				rand = ( rand * 65 + 17 ) & 0x7FFFFFFF;
				int dither = rand >> 25;
				rand = ( rand * 65 + 17 ) & 0x7FFFFFFF;
				dither -= rand >> 25;
				// "F-weighted" 3-tap noise shaping. Works well around 32khz.
				in = in - ( s1 * 13 -s2 * 8 + s3 ) / 8 + dither;
				s3 = s2;
				s2 = s1;
				// Rounding and quantization.
				int out = ( in + ( in & 0x80 ) ) >> 8;
				// Clipping.
				if( out < 0 ) out = 0;
				if( out > 255 ) out = 255;
				// Feedback.
				s1 = ( out << 8 ) - in;
				outputBuf[ idx ] = ( byte ) ( out - 128 );
			}
		} else {
			// No noise shaping or rounding, used when source is already 8 bit.
			for( int idx = 0, end = sampleData.length; idx < end; idx++ ) {
				outputBuf[ idx ] = ( byte ) ( sampleData[ idx ] >> 8 );
			}
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
		return resample( samplingRate, 0, false );
	}

	/* Return an AudioData instance with the specified sampling rate,
	   optionally adjusted in pitch by 96 per octave increments.
	   If the waveform is short and periodic in nature, better results
	   may be obtained if periodic is set to true. */
	public AudioData resample( int samplingRate, int pitch, boolean periodic ) {
		short[] inputBuf = new short[ sampleData.length + HTAPS * 2 ];
		if( periodic ) {
			for( int idx = 0; idx < inputBuf.length; idx++ ) {
				inputBuf[ idx ] = sampleData[ ( idx + sampleData.length * HTAPS - HTAPS + 1 ) % sampleData.length ];
			}
		} else {
			for( int idx = 0; idx < sampleData.length; idx++ ) {
				inputBuf[ idx + HTAPS - 1 ] = sampleData[ idx ];
			}
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
	
	private static void writeInt( OutputStream output, int value ) throws IOException {
		writeShort( output, value );
		writeShort( output, value >> 16 );
	}
	
	private static int readShort( InputStream input ) throws IOException {
		return ( input.read() & 0xFF ) | ( ( input.read() & 0xFF ) << 8 );
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
			AudioData audioData = new AudioData( new java.io.FileInputStream( inputPath ), channel );
			System.out.println( "Input Length: " + audioData.getLength() + " samples.");
			if( outputPath != null ) {
				if( length > 0 ) {
					audioData = audioData.crop( offset, length );
				}
				if( rate > 0 || tune != 96 ) {
					audioData = audioData.resample( rate > 0 ? rate : audioData.getSamplingRate(), tune - 96, false );
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
