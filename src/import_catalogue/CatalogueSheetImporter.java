package import_catalogue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;

import catalogue.Catalogue;
import catalogue.CatalogueBuilder;
import catalogue.ReleaseNotes;
import catalogue_browser_dao.CatalogueDAO;
import dcf_manager.Dcf;
import naming_convention.Headers;
import open_xml_reader.ResultDataSet;

public class CatalogueSheetImporter extends SheetImporter<Catalogue> {

	// the path where the db of the catalogue should be created
	private String dbPath;
	
	// the new catalogue
	private Catalogue catalogue;
	private Catalogue openedCatalogue;
	
	private String excelCatCode;
	
	/**
	 * Initialize the catalogue sheet importer
	 * @param dbPath the path where the db of the catalogue should be created
	 * @param data the catalogue sheet data
	 */
	public CatalogueSheetImporter( String dbPath ) {
		this.dbPath = dbPath;
	}
	
	/**
	 * Pass the opened catalogue if we are importing an excel
	 * file in it using the Import excel function.
	 * @param catalogue
	 */
	public void setOpenedCatalogue ( Catalogue openedCatalogue ) {
		this.openedCatalogue = openedCatalogue;
	}

	@Override
	public Catalogue getByResultSet(ResultDataSet rs) {
		
		Catalogue catalogue = null;

		// get the excel catalogue and
		// its code (used later)
		try {
			catalogue = getCatalogueFromExcel ( rs );
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// save the excel code in global variable
		// since if we have a local catalogue this
		// code will be overridden (and we need it
		// for the import)
		excelCatCode = catalogue.getCode();
		
		// if we have an opened catalogue we need to maintain
		// its code and version during the import otherwise
		// it will result in a different db path for storing
		// the data and thus giving errors
		if ( openedCatalogue != null ) {
			
			catalogue.setCode( openedCatalogue.getCode() );
			catalogue.setVersion( openedCatalogue.getVersion() );

			// if a local catalogue was set as code name and
			// label the local catalogue fields to maintain
			// the right names. Moreover we also maintain
			// the fields which are related to local catalogues
			// => version, status and local fields. Version is
			// important to be maintained otherwise a new database
			// will be created!
			if ( openedCatalogue.isLocal() ) {
				catalogue.setName( openedCatalogue.getName() );
				catalogue.setLabel( openedCatalogue.getLabel() );
				catalogue.setStatus( openedCatalogue.getStatus() );
				catalogue.setLocal( true );
				catalogue.setDbFullPath( openedCatalogue.getDbFullPath() );
				catalogue.setBackupDbPath( openedCatalogue.getBackupDbPath() );
			}
		}

		// save the catalogue as global variable
		this.catalogue = catalogue;
		
		// else the excel catalogue
		return catalogue;
	}
	


	/**
	 * Given a result set with catalogues meta data inside it, this function will
	 * create a catalogue object taking the meta data from the result set
	 * The function takes the current item of the results set and tries to get the
	 * catalogue data. Use a while(rs.next) loop for a result set with more than one catalogue
	 * USED FOR EXCEL CATALOGUES (they have a different naming convention to the ones used in the DB)
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	public static Catalogue getCatalogueFromExcel ( ResultSet rs ) throws SQLException {

		// create a catalogue using a builder
		CatalogueBuilder builder = new CatalogueBuilder();

		// set the catalogue meta data and create the catalogue object
		builder.setCode( rs.getString( Headers.CODE ) );
		builder.setVersion( rs.getString( Headers.VERSION ) );
		builder.setName( rs.getString( Headers.NAME ) );
		builder.setLabel( rs.getString( Headers.LABEL ) );
		builder.setScopenotes( rs.getString( Headers.SCOPENOTE ) );
		builder.setTermCodeMask( rs.getString( Headers.CAT_CODE_MASK ) );
		builder.setTermCodeLength( rs.getString( Headers.CAT_CODE_LENGTH ) );
		builder.setTermMinCode( rs.getString( Headers.CAT_MIN_CODE ) );
		builder.setAcceptNonStandardCodes( rs.getBoolean( Headers.CAT_ACCEPT_NOT_STD ) );
		builder.setGenerateMissingCodes( rs.getBoolean( Headers.CAT_GEN_MISSING ) );
		builder.setStatus( rs.getString( Headers.STATUS ) );
		builder.setCatalogueGroups( rs.getString( Headers.CAT_GROUPS ) );

		// set the dates with the adequate checks
		java.sql.Timestamp ts = rs.getTimestamp( Headers.LAST_UPDATE );
		
		if ( ts != null )
			builder.setLastUpdate( ts );

		ts = rs.getTimestamp( Headers.VALID_FROM );
		if ( ts != null )
			builder.setValidFrom( ts );

		ts = rs.getTimestamp( Headers.VALID_TO );
		if ( ts != null )
			builder.setValidTo( ts );

		builder.setDeprecated( rs.getBoolean( Headers.DEPRECATED ) );
		
		String desc = rs.getString( Headers.NOTES_DESCRIPTION );
		ts = rs.getTimestamp( Headers.NOTES_DATE );
		String vers = rs.getString( Headers.NOTES_VERSION );
		String note = rs.getString( Headers.NOTES_NOTE );
		
		builder.setReleaseNotes( new ReleaseNotes(desc, ts, vers, note, null) );
		
		// use as dcf type the one which was used to import the
		// catalogue, in fact, if we are importing an .ecf,
		// we import it in test if we are using test, otherwise
		// in production. Same for downloaded catalogues. For
		// local catalogues this is ignored
		builder.setCatalogueType( Dcf.dcfType );
		
		Catalogue catalogue = builder.build();
		
		// return the catalogue
		return catalogue;
	}
	
	

	@Override
	public Collection<Catalogue> getAllByResultSet(ResultDataSet rs) {
		return null;
	}

	@Override
	public void insert(Collection<Catalogue> data) {
		
		if ( data.isEmpty() )
			return;

		// get the catalogue and save it as global variable
		Iterator<Catalogue> iter = data.iterator();
		Catalogue catalogue = iter.next();

		// if anything was found => create a new catalogue
		// as default we create the catalogue using the official folder 
		// and the catalogue code and version
		// obtained from the excel sheet
		
		if ( dbPath == null && !catalogue.isLocal() ) {
			
			dbPath = catalogue.getDbPath();
			
			System.out.println( "Import: Db path is null, putting db in: " + dbPath );
			
			catalogue.setDbFullPath( dbPath );
		}

		// try to connect to the database. If it is not present we have an exception and thus we
		// create the database starting from scrach
		try {
			
			Connection con = catalogue.getConnection();
			con.close();
			
			// if no exception was thrown => the database exists and we have to delete it
			// delete the content of the old catalogue database
			System.out.println( "Deleting the database located in " + catalogue.getDbFullPath() );

			CatalogueDAO catDao = new CatalogueDAO();
			catDao.deleteDBRecords ( catalogue );
			
			System.out.println( "Freeing deleted memory..." );
			catDao.compressDatabase( catalogue );

			// set the id to the catalogue
			int id = catDao.getCatalogue( catalogue.getCode(), 
					catalogue.getVersion(), 
					catalogue.getCatalogueType() ).getId();

			catalogue.setId( id );
		}
		catch ( SQLException e ) {

			// otherwise the database does not exist => we create it

			System.out.println ( "Add " + catalogue + 
					" to the catalogue table in " + 
					catalogue.getDbFullPath() );

			CatalogueDAO catDao = new CatalogueDAO();

			// set the id to the catalogue
			int id = catDao.insert( catalogue );

			catalogue.setId( id );

			// create the standard database structure for
			// the new catalogue
			catDao.createDBTables( catalogue.getDbFullPath() );
		}
	}
	
	/**
	 * Get the imported catalogue. Note that you
	 * should call {@link #importSheet()} before
	 * this. Otherwise you will get null.
	 * @return
	 */
	public Catalogue getImportedCatalogue() {
		return catalogue;
	}
	
	/**
	 * Get the catalogue code contained into
	 * the catalogue sheet
	 * @return
	 */
	public String getExcelCode() {
		return excelCatCode;
	}

	@Override
	public void end() {}
}
