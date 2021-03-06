package br.com.topsys.database;

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import br.com.topsys.constant.TSConstant;
import br.com.topsys.constant.TSConstraint;
import br.com.topsys.database.factory.TSSequenceFactory;
import br.com.topsys.database.jdbc.TSDBList;
import br.com.topsys.exception.TSApplicationException;
import br.com.topsys.exception.TSBusinessException;
import br.com.topsys.exception.TSDataBaseException;
import br.com.topsys.exception.TSSystemException;
import br.com.topsys.util.TSLogUtil;
import br.com.topsys.util.TSPropertiesUtil;
import br.com.topsys.util.TSServiceLocatorUtil;

public abstract class TSDataBaseBrokerAb implements TSDataBaseBrokerIf {

	private Connection connection = null;

	protected PreparedStatement statement;

	protected ResultSet resultSet;

	protected CallableStatement callableStatement;

	protected int incremento = 1;
	
	protected String jndi;

	private StringBuilder sql = new StringBuilder();

	private final static String MENSAGEM_METODO_INVALIDO = "Esse met�do n�o pode ser executado quando utilizado a classe TSDataBaseBroker, ou seja quando o projeto utiliza EJB!";

	public TSDataBaseBrokerAb() {
		
		this.jndi = this
		.getProperty(TSConstant.JNDI_CONNECTION);
		
		this.connection = this.getConnection(this.jndi);

	}
	

	public TSDataBaseBrokerAb(String jndi) {
		this.jndi = jndi;
		this.connection = this.getConnection(jndi);
	}

	public Long getSequenceNextValue(String nome) {
		return TSSequenceFactory.getSequenceIf(this.jndi).getNextValue(nome);
	}
	
	public Long getSequenceNextValue(String nome,String className) {
		return TSSequenceFactory.getSequenceIf(this.jndi,className).getNextValue(nome);
	}

	public Long getSequenceCurrentValue(String nome) {
		return TSSequenceFactory.getSequenceIf(this.jndi).getCurrentValue(nome);
	}
	
	public Long getSequenceCurrentValue(String nome,String className) {
		return TSSequenceFactory.getSequenceIf(this.jndi,className).getCurrentValue(nome);
	}


	private String getProperty(String chave) {
		return TSPropertiesUtil.getInstance().getProperty(chave);
	}

	private Connection getConnection(String jndi) {
		Connection connection = null;
		DataSource dataSource = null;

		try {

			dataSource = (DataSource) TSServiceLocatorUtil.getInstance()
					.getDataSource(jndi);

			connection = dataSource.getConnection();

		} catch (Exception e) {
			throw new TSSystemException(e);
		}

		return connection;
	}

	public Connection getConnection() {

		return connection;
	}

	public void addBatch() throws TSApplicationException {
		try {

			statement.addBatch();

		} catch (SQLException e) {
			// this.rollbackAutoCommitFalse();
			this.close();

			this.catchException(e);

		
		}

	}

	public void beginTransaction() {
		TSLogUtil.getInstance().severe(MENSAGEM_METODO_INVALIDO);
		throw new TSSystemException(MENSAGEM_METODO_INVALIDO, null);

	}

	public void close() {
		try {

			if (this.resultSet != null) {

				this.resultSet.close();
			}
		} catch (SQLException e) {
		}

		try {
			if (this.callableStatement != null) {

				this.callableStatement.close();
			}
		} catch (SQLException e) {
		}

		try {
			if (this.statement != null) {

				this.statement.close();
			}
		} catch (SQLException e) {
		}

		try {
			if (this.connection != null) {

				this.connection.close();
			}
		} catch (SQLException e) {
		}

	}

	public void endTransaction() {
		TSLogUtil.getInstance().severe(MENSAGEM_METODO_INVALIDO);
		throw new TSSystemException(MENSAGEM_METODO_INVALIDO, null);

	}

	public TSDBList executeQuery() {
		TSDBList list = null;
		try {
			list = new TSDBList(this.resultSet = statement.executeQuery());

		} catch (SQLException e) {
			this.close();

			throw new TSSystemException(e);
		}

		return list;
	}

	public boolean getAutoCommit() {
		return false;
	}

	@SuppressWarnings("unchecked")
	public List getCollectionBean(Class bean, String... values) {
		List array = new ArrayList();

		try {

			this.resultSet = this.statement.executeQuery();

			for (;this.resultSet.next();) {
				array.add(this.getObjectPopulate(bean, values));
			}

		} catch (Exception e) {

			throw new TSSystemException(e);
		} finally {
			this.close();
		}

		return array;
	}

	public Object getObjectBean(Class bean, String... values) {
		Object objeto = null;
		try {

			this.resultSet = this.statement.executeQuery();

			if (this.resultSet.next()) {
				objeto = this.getObjectPopulate(bean, values);
			}

		} catch (Exception e) {

			throw new TSSystemException(e);
		} finally {
			this.close();
		}

		return objeto;
	}

	private Object getObjectPopulate(Class beanClass, String property[]) {

		/**
         * Esse metodo tive que fazer com reflexao pura, sem um framework como
         * PropertyUtils, Pois n�o resolvia uma parte do problema.
         */
        StringBuffer methodName = new StringBuffer();
        Object objBean = null;
        TSDBList list = null;

        try {

            objBean = beanClass.newInstance();

            list = new TSDBList(this.resultSet);

            for (int i = 0; i < property.length; i++) {

                String elementProperty[] = property[i].split("\\.");

                Class beanClassTmp = null;

                Object beanTmp = objBean;

                for (int y = 0; y < elementProperty.length; y++) {

                    beanClassTmp = beanTmp.getClass();

                    methodName.append("get");

                    methodName.append(elementProperty[y].substring(0, 1)
                            .toUpperCase());

                    methodName.append(elementProperty[y].substring(1));

                    Method methodGet = beanClassTmp.getMethod(methodName
                            .toString(), null);

                    String returnClassName = methodGet.getReturnType()
                            .getName();

                    methodName.replace(0, 1, "s");

                    Method methodSet = beanClassTmp.getMethod(methodName
                            .toString(), new Class[] { methodGet
                            .getReturnType() });

                    if (returnClassName.equals("java.lang.String")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getString(i + 1) });

                    } else if (returnClassName.equals("java.lang.Byte")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getByte(i + 1) });

                    } else if (returnClassName.equals("java.lang.Short")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getShort(i + 1) });

                    } else if (returnClassName.equals("java.lang.Integer")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getInteger(i + 1) });

                    } else if (returnClassName.equals("java.lang.Long")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getLong(i + 1) });

                    } else if (returnClassName.equals("java.lang.Float")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getFloat(i + 1) });

                    } else if (returnClassName.equals("java.lang.Double")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getDouble(i + 1) });

                    } else if (returnClassName.equals("java.lang.Boolean")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getBoolean(i + 1) });
                   
                    } else if (returnClassName.equals("java.sql.Timestamp")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getTimestamp(i + 1) });
                        
                    } else if (returnClassName.equals("java.util.Date")) {

                        methodSet.invoke(beanTmp, new Object[] { list
                                .getTimestamp(i + 1) });
                    
                    } else if (returnClassName.equals("java.math.BigDecimal")) {
                        methodSet.invoke(beanTmp, new Object[] { list
                                .getBigDecimal(i + 1) });

                    } else {
                        Object beanReturn = methodGet.invoke(beanTmp, null);
                        if (beanReturn == null) {
                            beanReturn = methodGet.getReturnType()
                                    .newInstance();
                            methodSet.invoke(beanTmp,
                                    new Object[] { beanReturn });
                        }
                        beanTmp = beanReturn;
                    }
                    methodName.setLength(0);
                }

            }

        } catch (Exception e) {
            throw new TSSystemException(e);
        }
        return objBean;

	}

	public void rollback() {
		TSLogUtil.getInstance().severe(MENSAGEM_METODO_INVALIDO);
		throw new TSSystemException(MENSAGEM_METODO_INVALIDO, null);

	}

	public void set(Object... values) {

		if (values == null) {

			throw new IllegalArgumentException();

		}

		for (int x = 0; x < values.length; x++) {

			this.set(values[x]);

		}

	}

	public void set(Object value) {
		try {

			if (value == null) {

				statement.setNull(this.incremento++, Types.NULL);

			} else {

				if (value instanceof Timestamp) {

					statement.setTimestamp(this.incremento++, (Timestamp)value);

				} else if (value instanceof Date) {

					statement.setDate(this.incremento++, new java.sql.Date(
							((Date) value).getTime()));

				} else {

					statement.setObject(this.incremento++, value);

				}

			}
		} catch (SQLException e) {
			this.close();

			throw new TSSystemException(e);
		}
	}

	public void setPropertySQL(String query, Object... objects) {
		try {

			statement = this.getConnection().prepareStatement(
					this.getProperty(query));
			this.set(objects);

		} catch (Exception e) {
			this.close();
			throw new TSSystemException(e);

		}

	}

	public void setPropertySQL(String query) {
		try {

			statement = this.getConnection().prepareStatement(
					this.getProperty(query));

		} catch (Exception e) {
			this.close();
			throw new TSSystemException(e);

		}

	}

	public void setSQL(String query, Object... objects) {
		try {

			statement = this.getConnection().prepareStatement(query);
			this.set(objects);

		} catch (SQLException e) {
			this.close();

			throw new TSSystemException(e);
		}

	}

	public void setSQL(String query) {
		try {

			statement = this.getConnection().prepareStatement(query);

		} catch (SQLException e) {
			this.close();

			throw new TSSystemException(e);
		}

	}

	protected void catchException(SQLException e) throws TSApplicationException {
		
		if (String.valueOf(TSConstraint.getUnique()).equals(e.getSQLState()) || e.getErrorCode() == TSConstraint.getUnique()) {

			throw new TSBusinessException(TSConstant.MENSAGEM_UNIQUE);

		} else if (String.valueOf(TSConstraint.getForeignKey()).equals(e.getSQLState()) || e.getErrorCode() == TSConstraint.getForeignKey()) {

			throw new TSBusinessException(TSConstant.MENSAGEM_FOREIGNKEY);
		
		}else if (String.valueOf(TSConstraint.getRaiseException()).equals(e.getSQLState()) || e.getErrorCode() == Integer.parseInt(TSConstraint.getRaiseException())) {

				throw new TSDataBaseException(e.getMessage());
			
		
		}else{
			throw new TSSystemException(e);
		}
	}

	public void addPropertySQL(String chave) {
		sql.append(this.getProperty(chave));
		sql.append(" ");

	}

	public String getPropertySQL() {
		return this.sql.toString();

	}

}
